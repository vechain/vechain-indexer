package org.vechain.indexer.stargate.token

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorRepository

/**
 * StargateEventApplier
 *
 * Applies Stargate events to token snapshots:
 * - Delegations
 * - Transfers
 * - Minting / burning
 * - Manager changes
 * - Boosts
 * - Rewards claims
 * - Exit requests and withdrawals
 */
@Profile("stargate", "stargate-token")
@Service
class StargateEventService(
    private val validatorDelegationService: ValidatorDelegationService,
    private val validatorRepository: ValidatorRepository,
    @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
    private val stargateDelegationContract: String,
) {
    /** Apply event-driven mutations to token snapshots. */
    suspend fun handleStargateEvents(
        events: List<IndexedEvent>,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        validators: Map<String, Validator>,
        existingTokens: MutableList<StargateToken>,
    ) {
        events
            .filter { it.eventType == "ValidationSignaledExit" }
            .forEach { event ->
                handleValidationSignaledExit(
                    event,
                    latestTokenSnapshots,
                    validators,
                    existingTokens,
                )
            }

        events
            .filter { it.eventType == "ValidationWithdrawn" }
            .forEach { event ->
                handleValidationWithdrawn(event, latestTokenSnapshots, existingTokens)
            }

        // Group remaining events by tokenId (fallback to nodeId)
        val groupedEvents =
            events
                .filter { it.eventType !in VALIDATOR_LIFECYCLE_EVENTS }
                .groupBy {
                    it.params.getAsString("tokenId")?.takeIf { id -> id.isNotBlank() }
                        ?: it.params.getAsString(
                            "nodeId"
                        ) // TODO: Remove once Hayabusa live on Mainnet
                }
                .filterKeys { it != null } // skip events without either ID
                .mapValues { (_, tokenEvents) ->
                    tokenEvents.sortedWith(
                        compareByDescending<IndexedEvent> { it.eventType == "TokenMinted" }
                            .thenBy { it.blockNumber }
                    )
                }

        groupedEvents.forEach { (tokenId, tokenEvents) ->
            val id = tokenId ?: return@forEach // safety check
            var current: StargateToken? = latestTokenSnapshots[id]

            tokenEvents.forEach { event ->
                current = processEvent(event, tokenId, current, validators, existingTokens)
            }
            if (current != null) latestTokenSnapshots[tokenId] = current
        }
    }

    // Process a single event for a token
    private fun processEvent(
        event: IndexedEvent,
        tokenId: String,
        base: StargateToken?,
        validators: Map<String, Validator>,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken? =
        when (event.eventType) {
            "TokenMinted" -> handleTokenMinted(event, tokenId)
            "TokenBurned" -> handleTokenUnstaked(event, base!!, existingTokens)
            "Transfer" -> handleTokenTransfer(event, base!!, existingTokens)
            "DelegationInitiated" -> handleDelegate(event, base!!, validators, existingTokens)
            "DelegationExitRequested" -> handleDelegateExitRequest(event, base!!, existingTokens)
            "DelegationWithdrawn" -> handleExitDelegate(event, base!!, existingTokens)
            "TokenManagerAdded" -> handleManagerAdded(event, base!!, existingTokens)
            "TokenManagerRemoved" -> handleManagerRemoved(event, base!!, existingTokens)
            "MaturityPeriodBoosted" -> handleTokenBoosted(event, base!!, existingTokens)
            "NodeDelegated" -> handleNodeManagementEvent(event, base, existingTokens)
            "BaseVTHORewardsClaimed",
            "DelegationRewardsClaimed" -> handleRewardsClaimed(event, base!!, existingTokens)
            else -> base
        }

    private fun handleValidationSignaledExit(
        event: IndexedEvent,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        validators: Map<String, Validator>,
        existingTokens: MutableList<StargateToken>,
    ) {
        val validatorId = event.params.getAsString("validator")?.lowercase() ?: return
        val exitAt = resolveValidatorExitBlock(validatorId, event.blockNumber, validators)
        val affectedTokens =
            latestTokenSnapshots.values.filter { it.validatorId?.lowercase() == validatorId }

        affectedTokens
            .filter {
                when (it.delegationStatus) {
                    Status.NONE -> false
                    Status.EXITING ->
                        exitAt == null || (it.delegationNextPeriod ?: Long.MAX_VALUE) <= exitAt
                    else -> true
                }
            }
            .forEach { token ->
                existingTokens.add(token)
                latestTokenSnapshots[token.tokenId] =
                    token.copy(
                        version = token.version + 1,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        delegationStatus = Status.EXITING,
                        delegationNextPeriod = exitAt ?: token.delegationNextPeriod,
                        validatorExiting = true,
                    )
            }
    }

    private fun handleValidationWithdrawn(
        event: IndexedEvent,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        existingTokens: MutableList<StargateToken>,
    ) {
        val validatorId = event.params.getAsString("validator")?.lowercase() ?: return

        latestTokenSnapshots.values
            .filter {
                it.validatorId?.lowercase() == validatorId && it.delegationStatus != Status.NONE
            }
            .forEach { token ->
                existingTokens.add(token)
                latestTokenSnapshots[token.tokenId] =
                    token.copy(
                        version = token.version + 1,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        delegationStatus = Status.NONE,
                        validatorId = null,
                        delegationNextPeriod = null,
                        delegationPeriodLength = null,
                        validatorExiting = null,
                    )
            }
    }

    // ------------------------------------------------------------------------
    // Event Handlers
    // ------------------------------------------------------------------------

    // Legacy node management event handler
    private fun handleNodeManagementEvent(
        event: IndexedEvent,
        base: StargateToken?,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken? =
        if (base == null) {
            null
        } else if (event.params.getAsBoolean("delegated") == true) {
            handleManagerAdded(event, base, existingTokens)
        } else {
            handleManagerRemoved(event, base, existingTokens)
        }

    // Rewards claimed event
    private fun handleManagerAdded(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            manager =
                event.params.getAsString("manager")
                    ?: event.params.getAsString(
                        "delegatee"
                    ), // TODO: Remove once Hayabusa live on Mainnet
        )
    }

    // Token manager removed event
    fun handleManagerRemoved(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            manager = null,
        )
    }

    // Rewards claimed event
    fun handleRewardsClaimed(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        if (event.address == stargateDelegationContract) {
            val rewards =
                if (event.eventType == "BaseVTHORewardsClaimed") {
                    event.params.getAsBigInteger("amount")!!
                } else {
                    event.params.getAsBigInteger("rewards")!!
                }
            existingTokens.add(base)
            return base.copy(
                totalBootstrapRewardsClaimed = base.totalBootstrapRewardsClaimed + rewards,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                version = base.version + 1,
            )
        } else {
            existingTokens.add(base)
            return base.copy(
                totalRewardsClaimed =
                    base.totalRewardsClaimed + event.params.getAsBigInteger("amount")!!,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                version = base.version + 1,
            )
        }
    }

    // Delegation withdrawn event
    private fun handleExitDelegate(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        if (base.delegationStatus == Status.NONE) {
            return base
        }

        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegationStatus = Status.NONE,
            validatorId = null,
        )
    }

    // Token delegation event
    private fun handleDelegate(
        event: IndexedEvent,
        base: StargateToken,
        validators: Map<String, Validator>,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        val validator =
            event.params.getAsString("validator")?.lowercase()
                ?: throw IllegalStateException("Validator not found")

        val (periodLength, nextCycleStart) =
            resolveCycleInfo(validator, event.blockNumber, validators)

        existingTokens.add(base)
        return base.copy(
            validatorId = validator,
            delegationStatus = Status.QUEUED,
            delegationNextPeriod = nextCycleStart,
            delegationPeriodLength = periodLength,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            version = base.version + 1,
        )
    }

    // Token was boosted to skip maturity period
    private fun handleTokenBoosted(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            boosted = true,
        )
    }

    // Delegation exit request event
    private fun handleDelegateExitRequest(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        // Legacy delegation contract - no state changes
        if (event.address == stargateDelegationContract) return base

        return when (base.delegationStatus) {
            Status.NONE -> base

            Status.QUEUED -> {
                existingTokens.add(base)
                base.copy(
                    version = base.version + 1,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    delegationStatus = Status.NONE,
                )
            }

            else -> {
                existingTokens.add(base)
                base.copy(
                    version = base.version + 1,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    delegationStatus = Status.EXITING,
                    delegationNextPeriod =
                        validatorDelegationService.resolveNextCycleBlock(
                            base.delegationNextPeriod,
                            base.delegationPeriodLength!!,
                            event.blockNumber,
                        ),
                )
            }
        }
    }

    // Token transfer event
    private fun handleTokenTransfer(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = event.params.getAsString("to")!!,
        )
    }

    // TokenBurned event is used for unstaking tokens
    private fun handleTokenUnstaked(
        event: IndexedEvent,
        base: StargateToken,
        existingTokens: MutableList<StargateToken>,
    ): StargateToken {
        existingTokens.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = Address.ZERO_ADDRESS,
            manager = null,
            vetStaked = BigInteger.ZERO,
            delegationStatus = Status.NONE,
            validatorId = null,
        )
    }

    // TokenMinted event is used for staking tokens
    private fun handleTokenMinted(event: IndexedEvent, tokenId: String): StargateToken =
        StargateToken(
            tokenId = tokenId,
            version = 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            owner = event.params.getAsString("owner")!!,
            level = TokenLevel.fromOrdinal(event.params.getAsString("levelId")!!.toInt())!!,
            vetStaked = event.params.getAsBigInteger("vetAmountStaked")!!,
            migrated = event.params.getAsBoolean("migrated")!!,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            delegationStatus = Status.NONE,
            boosted = false,
        )

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun resolveCycleInfo(
        validatorId: String,
        blockNumber: Long,
        validators: Map<String, Validator>,
    ): Pair<Long?, Long> {
        val validator = resolveValidator(validatorId, validators) ?: return null to 0L
        val period = validator.cyclePeriodLength?.takeIf { it > 0L } ?: return null to 0L
        return period to nextCycleStart(validator, blockNumber)
    }

    private fun resolveValidatorExitBlock(
        validatorId: String,
        blockNumber: Long,
        validators: Map<String, Validator>,
    ): Long? {
        val validator = resolveValidator(validatorId, validators) ?: return null
        validator.exitBlock
            ?.takeIf { it > 0L }
            ?.let {
                return it
            }
        return nextCycleStart(validator, blockNumber).takeIf { it > 0L }
    }

    private fun resolveValidator(
        validatorId: String,
        validators: Map<String, Validator>,
    ): Validator? {
        val normalized = validatorId.lowercase()
        return validators[normalized] ?: validatorRepository.findByIdOrNull(normalized)
    }

    private fun nextCycleStart(validator: Validator, blockNumber: Long): Long {
        val startBlock = validator.startBlock ?: 0L
        val period = validator.cyclePeriodLength ?: 0L
        if (startBlock == 0L || period <= 0L) return 0L
        val offset = blockNumber - startBlock
        val positionInCycle = offset % period
        val currentCycleStart = blockNumber - positionInCycle
        return currentCycleStart + period
    }

    companion object {
        private val VALIDATOR_LIFECYCLE_EVENTS =
            setOf("ValidationSignaledExit", "ValidationWithdrawn")
    }
}
