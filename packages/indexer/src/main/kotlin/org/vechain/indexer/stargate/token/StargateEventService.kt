package org.vechain.indexer.stargate.token

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorSnapshot
import org.vechain.indexer.validator.logic.ValidatorCalculator

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
    @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
    private val stargateDelegationContract: String,
) {
    /** Apply event-driven mutations to token snapshots. */
    fun handleStargateEvents(
        events: List<IndexedEvent>,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        tokensToArchive: MutableList<StargateToken>,
    ) {
        val validatorEvents = events.filter { it.eventType == "ValidatorExitRequested" }
        validatorEvents.forEach { event ->
            handleValidatorExitRequested(
                event,
                latestTokenSnapshots,
                validatorSnapshots,
                tokensToArchive,
            )
        }

        val groupedEvents =
            events
                .filter { it.eventType != "ValidatorExitRequested" }
                .groupBy { it.params.getAsString("tokenId")!! }
                .mapValues { (_, tokenEvents) ->
                    tokenEvents.sortedWith(
                        compareByDescending<IndexedEvent> { it.eventType == "TokenMinted" }
                            .thenBy { it.blockNumber }
                    )
                }

        groupedEvents.forEach { (tokenId, tokenEvents) ->
            var current: StargateToken? = latestTokenSnapshots[tokenId]
            tokenEvents.forEach { event ->
                current = processEvent(event, tokenId, current, validatorSnapshots, tokensToArchive)
            }
            if (current != null) latestTokenSnapshots[tokenId] = current
        }
    }

    private fun processEvent(
        event: IndexedEvent,
        tokenId: String,
        base: StargateToken?,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken? =
        when (event.eventType) {
            "TokenMinted" -> handleTokenMinted(event, tokenId)
            "TokenBurned" -> handleTokenUnstaked(event, base!!, tokensToArchive)
            "Transfer" -> handleTokenTransfer(event, base!!, tokensToArchive)
            "DelegationInitiated" ->
                handleDelegate(event, base!!, validatorSnapshots, tokensToArchive)
            "DelegationExitRequested" -> handleDelegateExitRequest(event, base!!, tokensToArchive)
            "DelegationWithdrawn" -> handleExitDelegate(base!!, tokensToArchive)
            "TokenManagerAdded" -> handleManagerAdded(event, base!!, tokensToArchive)
            "TokenManagerRemoved" -> handleManagerRemoved(event, base!!, tokensToArchive)
            "MaturityPeriodBoosted" -> handleTokenBoosted(event, base!!, tokensToArchive)
            "NodeDelegated" -> handleNodeManagementEvent(event, base!!, tokensToArchive)
            "BaseVTHORewardsClaimed",
            "DelegationRewardsClaimed" -> handleRewardsClaimed(event, base!!, tokensToArchive)
            else -> base
        }

    // ------------------------------------------------------------------------
    // Event Handlers
    // ------------------------------------------------------------------------

    // Legacy node management event handler
    private fun handleNodeManagementEvent(
        event: IndexedEvent,
        base: StargateToken,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken =
        if (event.params.getAsBoolean("delegated") == true) {
            handleManagerAdded(event, base, tokensToArchive)
        } else {
            handleManagerRemoved(event, base, tokensToArchive)
        }

    // Rewards claimed event
    private fun handleManagerAdded(
        event: IndexedEvent,
        base: StargateToken,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        tokensToArchive.add(base)
        return base.copy(
            version = base.version + 1,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            manager = event.params.getAsString("manager")!!,
        )
    }

    // Token manager removed event
    fun handleManagerRemoved(
        event: IndexedEvent,
        base: StargateToken,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        tokensToArchive.add(base)
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
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        if (event.address == stargateDelegationContract) {
            val rewards =
                if (event.eventType == "BaseVTHORewardsClaimed") {
                    event.params.getAsBigInteger("amount")!!
                } else {
                    event.params.getAsBigInteger("rewards")!!
                }
            tokensToArchive.add(base)
            return base.copy(
                totalBootstrapRewardsClaimed = base.totalBootstrapRewardsClaimed + rewards,
                version = base.version + 1,
            )
        } else {
            tokensToArchive.add(base)
            return base.copy(
                totalRewardsClaimed =
                    base.totalRewardsClaimed + event.params.getAsBigInteger("amount")!!,
                version = base.version + 1,
            )
        }
    }

    // Delegation withdrawn event
    private fun handleExitDelegate(
        base: StargateToken,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        if (base.delegationStatus == Status.NONE) {
            return base
        }

        tokensToArchive.add(base)
        return base.copy(
            version = base.version + 1,
            delegationStatus = Status.NONE,
            validatorId = null,
        )
    }

    // Token delegation event
    private fun handleDelegate(
        event: IndexedEvent,
        base: StargateToken,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        val validator =
            event.params.getAsString("validator")
                ?: throw IllegalStateException("Validator not found")

        val (periodLength, nextCycleStart) =
            resolveCycleInfo(validator, event.blockNumber, validatorSnapshots)

        tokensToArchive.add(base)
        return base.copy(
            validatorId = validator,
            delegationStatus = Status.QUEUED,
            delegationNextPeriod = nextCycleStart,
            delegationPeriodLength = periodLength,
            version = base.version + 1,
        )
    }

    // Token was boosted to skip maturity period
    private fun handleTokenBoosted(
        event: IndexedEvent,
        base: StargateToken,
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        tokensToArchive.add(base)
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
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        // Legacy delegation contract - no state changes
        if (event.address == stargateDelegationContract) return base

        return when (base.delegationStatus) {
            Status.NONE -> base

            Status.QUEUED -> {
                tokensToArchive.add(base)
                base.copy(
                    version = base.version + 1,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    delegationStatus = Status.NONE,
                )
            }

            else -> {
                tokensToArchive.add(base)
                base.copy(
                    version = base.version + 1,
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
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        tokensToArchive.add(base)
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
        tokensToArchive: MutableList<StargateToken>,
    ): StargateToken {
        tokensToArchive.add(base)
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

    private fun handleValidatorExitRequested(
        event: IndexedEvent,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        tokensToArchive: MutableList<StargateToken>,
    ) {
        val validatorId =
            event.params.getAsString("validatorId")
                ?: throw IllegalArgumentException("ValidatorExitRequested missing validatorId")

        val exitAt =
            validatorDelegationService.getValidatorExitBlock(validatorId, validatorSnapshots)
        val affectedTokens = latestTokenSnapshots.values.filter { it.validatorId == validatorId }

        affectedTokens
            .filter {
                when (it.delegationStatus) {
                    Status.NONE -> false
                    Status.EXITING -> (it.delegationNextPeriod ?: Long.MAX_VALUE) <= exitAt
                    else -> true
                }
            }
            .forEach { token ->
                tokensToArchive.add(token)
                val snapshot =
                    token.copy(
                        version = token.version + 1,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        delegationStatus = Status.EXITING,
                        validatorExiting = true,
                    )
                latestTokenSnapshots[token.tokenId] = snapshot
            }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun resolveCycleInfo(
        validatorId: String,
        blockNumber: Long,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): Pair<Long, Long> {
        val snapshot = validatorSnapshots[validatorId]
        return if (snapshot != null) {
            snapshot.stakingPeriodLength to
                ValidatorCalculator.calculateNextCycleStart(snapshot, blockNumber)
        } else {
            validatorDelegationService.getValidatorPeriodInfo(validatorId, blockNumber)
        }
    }
}
