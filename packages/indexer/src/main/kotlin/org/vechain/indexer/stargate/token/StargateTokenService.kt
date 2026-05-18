package org.vechain.indexer.stargate.token

import kotlin.collections.plus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorRepository

/**
 * StargateService
 *
 * Orchestrates block processing:
 * - Loads validator state (via ValidatorService)
 * - Detects validator exits/disappearances
 * - Loads and mutates token snapshots
 * - Applies Stargate events (via StargateEventService)
 * - Persists updated snapshots
 */
@Profile("stargate", "stargate-token")
@Service
open class StargateTokenService(
    private val stargateTokenRepository: StargateTokenRepository,
    private val eventService: StargateEventService,
    private val validatorRepository: ValidatorRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    @param:Value("\${indexer.start-block.validator}") private val validatorStartBlock: Long,
) {
    private var cachedValidators: Set<String> = emptySet()
    private var cachedValidatorsLoaded = false

    // Validator cycle fields (cyclePeriodLength / startBlock / exitBlock) are only written by
    // ValidatorService at cold start and every epoch boundary, so we reload on the same cadence
    // instead of scanning the validator collection every block.
    private var activeValidators: Map<String, Validator> = emptyMap()
    private var activeValidatorsLoaded = false

    /** Main entry point for processing a block. */
    open suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<Collection<StargateToken>, List<StargateToken>> {
        // NFT lifecycle events (mints, transfers, manager changes) can fire in the gap between
        // the Stargate NFT deployment and the validator indexer's start block — index them.
        // Only the validator-collection read is gated; the validator map stays empty until the
        // parent indexer has data, and delegation events don't fire on-chain before then.
        val validatorIndexerActive = block.number >= validatorStartBlock
        val validatorsRefreshed =
            validatorIndexerActive && (!activeValidatorsLoaded || isEpochBoundary(block.number))
        if (validatorsRefreshed) {
            activeValidators =
                validatorRepository.findByStatusNot(Status.WITHDRAWN).associateBy {
                    it.id.lowercase()
                }
            activeValidatorsLoaded = true
        }

        val removedValidators = checkMissingValidators(activeValidators, validatorsRefreshed)

        val lifecycleValidators = findValidatorLifecycleEvents(events)
        val existingTokens = mutableListOf<StargateToken>()

        // DB lookups
        val latestTokenSnapshots =
            loadRelevantTokenSnapshots(
                block,
                events,
                removedValidators,
                lifecycleValidators,
                activeValidators,
                validatorsRefreshed,
                existingTokens,
            )

        // Mutations
        processDelegationStatusTransitions(block, latestTokenSnapshots, existingTokens)
        handleValidatorsDisappearedSnapshots(
            removedValidators,
            block,
            latestTokenSnapshots,
            existingTokens,
        )
        eventService.handleStargateEvents(
            events,
            latestTokenSnapshots,
            activeValidators,
            existingTokens,
        )

        // Only return tokens that were actually modified or newly minted (version 1).
        // Unmodified tokens loaded from DB at version > 1 have no entry in existingTokens,
        // which would violate the InlineVersionService invariant.
        val modifiedTokenIds = existingTokens.map { it.tokenId }.toSet()
        val updated =
            latestTokenSnapshots.values.filter { it.tokenId in modifiedTokenIds || it.version <= 1 }
        return updated to existingTokens
    }

    /** Persist updated token snapshots. */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(tokens: Collection<StargateToken>, existing: List<StargateToken>) {
        if (tokens.isEmpty()) return
        saveVersionedDocuments(
            tokens.toList(),
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
            inlineVersioningProperties.minVersions,
        )
    }

    open fun invalidateCache() {
        cachedValidators = emptySet()
        cachedValidatorsLoaded = false
        activeValidators = emptyMap()
        activeValidatorsLoaded = false
    }

    // ------------------------------------------------------------------------
    // Snapshot Loading
    // ------------------------------------------------------------------------

    private fun loadRelevantTokenSnapshots(
        block: Block,
        events: List<IndexedEvent>,
        removedValidators: Set<String>,
        lifecycleValidators: Set<String>,
        validators: Map<String, Validator>,
        validatorsRefreshed: Boolean,
        existingTokens: MutableList<StargateToken>,
    ): MutableMap<String, StargateToken> {
        // 1. Gather candidate snapshots from DB
        val snapshotsByTokenId =
            events
                .mapNotNull {
                    it.params.getAsString("tokenId")?.takeIf { id -> id.isNotBlank() }
                        ?: it.params.getAsString(
                            "nodeId"
                        ) // TODO: Remove once Hayabusa live on Mainnet
                }
                .filter { it.isNotBlank() }
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?.let { ids -> stargateTokenRepository.findAllById(ids).toList() }
                .orEmpty()

        val snapshotsByValidatorId =
            (removedValidators + lifecycleValidators)
                .takeIf { it.isNotEmpty() }
                ?.let { stargateTokenRepository.findByValidatorIdIn(it) }
                .orEmpty()

        val dueSnapshots =
            stargateTokenRepository.findByDelegationNextPeriodAndDelegationStatusIn(
                listOf(0L, block.number),
                listOf(Status.QUEUED.name, Status.EXITING.name),
            )

        // 2. Split due snapshots into unknown vs transitioning
        val (unknown, transitioning) =
            dueSnapshots.partition {
                it.delegationNextPeriod == 0L && it.delegationStatus == Status.QUEUED
            }

        // 3. Resolve unknown start blocks
        val resolvedUnknowns =
            resolveUnknownDelegations(
                unknown,
                block,
                validators,
                validatorsRefreshed,
                existingTokens,
            )

        // 4. Merge everything together
        return (snapshotsByTokenId + snapshotsByValidatorId + transitioning + resolvedUnknowns)
            .associateBy { it.tokenId }
            .toMutableMap()
    }

    // ------------------------------------------------------------------------
    // Transitions
    // ------------------------------------------------------------------------

    /** Applies scheduled delegation status transitions when their period ends. */
    fun processDelegationStatusTransitions(
        block: Block,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        existingTokens: MutableList<StargateToken>,
    ) {
        latestTokenSnapshots.values
            .filter {
                (it.delegationStatus == Status.QUEUED || it.delegationStatus == Status.EXITING) &&
                    it.delegationNextPeriod == block.number
            }
            .forEach { token ->
                existingTokens.add(token)
                latestTokenSnapshots[token.tokenId] =
                    token.copy(
                        version = token.version + 1,
                        delegationStatus =
                            if (token.delegationStatus == Status.EXITING) {
                                Status.NONE
                            } else {
                                Status.ACTIVE
                            },
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        validatorId =
                            if (token.delegationStatus == Status.EXITING) {
                                Address.ZERO_ADDRESS
                            } else {
                                token.validatorId
                            },
                    )
            }
    }

    /**
     * Resolves delegations with unknown start blocks.
     *
     * Unknowns are resolved only after a refreshed validator cache so this path does not add a
     * per-block validator collection read.
     */
    private fun resolveUnknownDelegations(
        unknown: List<StargateToken>,
        block: Block,
        validators: Map<String, Validator>,
        validatorsRefreshed: Boolean,
        existingTokens: MutableList<StargateToken>,
    ): List<StargateToken> {
        if (unknown.isEmpty()) return emptyList()
        if (!validatorsRefreshed) return emptyList()

        val grouped: Map<String, List<StargateToken>> =
            unknown.filter { it.validatorId != null }.groupBy { it.validatorId!!.lowercase() }

        val resolved = mutableListOf<StargateToken>()

        grouped.keys.forEach { validatorId ->
            val validator =
                validators[validatorId] ?: validatorRepository.findByIdOrNull(validatorId)
            val startBlock = validator?.startBlock ?: 0L

            if (startBlock != 0L) {
                grouped[validatorId]?.forEach { existing ->
                    existingTokens.add(existing)
                    resolved +=
                        existing.copy(
                            delegationNextPeriod = startBlock,
                            delegationPeriodLength =
                                validator?.cyclePeriodLength ?: existing.delegationPeriodLength,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
                            version = existing.version + 1,
                        )
                }
            }
        }

        return resolved
    }

    // ------------------------------------------------------------------------
    // Validators
    // ------------------------------------------------------------------------

    fun handleValidatorsDisappearedSnapshots(
        validatorIds: Set<String>,
        block: Block,
        latestTokenSnapshots: MutableMap<String, StargateToken>,
        existingTokens: MutableList<StargateToken>,
    ) {
        latestTokenSnapshots.values
            .filter { it.validatorId in validatorIds }
            .forEach { token ->
                existingTokens.add(token)
                latestTokenSnapshots[token.tokenId] =
                    token.copy(
                        version = token.version + 1,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        delegationStatus = Status.NONE,
                        validatorId = null,
                        delegationNextPeriod = null,
                        delegationPeriodLength = null,
                        validatorExiting = null,
                    )
            }
    }

    /** Detect validators that disappeared compared to previous state. */
    private fun checkMissingValidators(
        validators: Map<String, Validator>,
        validatorsRefreshed: Boolean,
    ): Set<String> {
        if (!validatorsRefreshed) return emptySet()
        val currentValidators = validators.keys
        if (currentValidators.isEmpty()) return emptySet()

        if (!cachedValidatorsLoaded) {
            cachedValidators =
                stargateTokenRepository
                    .findAllDistinctValidatorIds()
                    .filterNotNull()
                    .map { it.lowercase() }
                    .toSet()
            cachedValidatorsLoaded = true
        }

        val removed = cachedValidators.minus(currentValidators)

        cachedValidators = currentValidators
        return removed
    }

    /** Extract validator IDs from validator lifecycle events. */
    private fun findValidatorLifecycleEvents(events: List<IndexedEvent>): Set<String> =
        events
            .filter {
                it.eventType == "ValidationSignaledExit" || it.eventType == "ValidationWithdrawn"
            }
            .mapNotNull { it.params.getAsString("validator")?.lowercase() }
            .toSet()

    private fun isEpochBoundary(blockNumber: Long): Boolean = blockNumber % EPOCH_LENGTH == 0L

    companion object {
        const val EPOCH_LENGTH = 180L
    }
}
