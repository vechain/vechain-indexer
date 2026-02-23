package org.vechain.indexer.stargate.token

import kotlin.collections.plus
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorSnapshot

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
    private val validatorDelegationService: ValidatorDelegationService,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private var cachedValidators: Set<String> = emptySet()

    /** Main entry point for processing a block. */
    open suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
        events: List<IndexedEvent>,
    ): Pair<Collection<StargateToken>, List<StargateToken>> {
        val validatorSnapshots = validatorDelegationService.decodeValidatorSnapshots(callResponses)
        val removedValidators = checkMissingValidators(validatorSnapshots)

        val exitingValidators = findDelegationsFromExits(events)

        // DB lookups
        val latestTokenSnapshots =
            loadRelevantTokenSnapshots(
                block,
                events,
                removedValidators,
                exitingValidators,
                validatorSnapshots,
            )

        val tokensToArchive = mutableListOf<StargateToken>()

        // Mutations
        processDelegationStatusTransitions(block, latestTokenSnapshots, tokensToArchive)
        handleValidatorsDisappearedSnapshots(
            removedValidators,
            block,
            latestTokenSnapshots,
            tokensToArchive,
        )
        eventService.handleStargateEvents(
            events,
            latestTokenSnapshots,
            validatorSnapshots,
            tokensToArchive,
        )

        return latestTokenSnapshots.values to tokensToArchive
    }

    /** Persist updated token snapshots. */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(tokens: Collection<StargateToken>, archive: List<StargateToken>) {
        if (tokens.isEmpty()) return
        saveVersionedDocuments(
            tokens.toList(),
            archive,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    // ------------------------------------------------------------------------
    // Snapshot Loading
    // ------------------------------------------------------------------------

    private suspend fun loadRelevantTokenSnapshots(
        block: Block,
        events: List<IndexedEvent>,
        removedValidators: Set<String>,
        exitingValidators: List<String>,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
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
            (removedValidators + exitingValidators)
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
        val resolvedUnknowns = resolveUnknownDelegations(unknown, block, validatorSnapshots)

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
        archive: MutableList<StargateToken>,
    ) {
        latestTokenSnapshots.values
            .filter {
                (it.delegationStatus == Status.QUEUED || it.delegationStatus == Status.EXITING) &&
                    it.delegationNextPeriod == block.number
            }
            .forEach { token ->
                archive.add(token)
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
     * Uses validator snapshots if available, otherwise queries the chain.
     */
    private suspend fun resolveUnknownDelegations(
        unknown: List<StargateToken>,
        block: Block,
        validatorsSnapshots: Map<String, ValidatorSnapshot>,
    ): List<StargateToken> {
        if (unknown.isEmpty()) return emptyList()

        val validators: Map<String, List<StargateToken>> =
            unknown.filter { it.validatorId != null }.groupBy { it.validatorId!! }

        val snapshotEmpty = validatorsSnapshots.isEmpty()
        val responses: List<InspectionResult> =
            if (snapshotEmpty) {
                validatorDelegationService.fetchValidationPeriodDetails(validators.keys.toList())
            } else {
                emptyList()
            }

        val resolved = mutableListOf<StargateToken>()

        validators.keys.forEachIndexed { index, validatorId ->
            val startBlock =
                if (snapshotEmpty) {
                    validatorDelegationService.determineStartBlock(responses[index])
                } else {
                    validatorsSnapshots[validatorId]?.startBlock ?: 0L
                }

            if (startBlock != 0L) {
                validators[validatorId]?.forEach { existing ->
                    resolved +=
                        existing.copy(
                            delegationNextPeriod = startBlock,
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
        tokensToArchive: MutableList<StargateToken>,
    ) {
        latestTokenSnapshots.values
            .filter { it.validatorId in validatorIds }
            .forEach { token ->
                tokensToArchive.add(token)
                latestTokenSnapshots[token.tokenId] =
                    token.copy(
                        version = token.version + 1,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        delegationStatus = Status.NONE,
                        validatorId = null,
                    )
            }
    }

    /** Detect validators that disappeared compared to previous state. */
    private fun checkMissingValidators(
        validatorsSnapshots: Map<String, ValidatorSnapshot>
    ): Set<String> {
        val currentValidators = validatorsSnapshots.keys
        if (currentValidators.isEmpty()) return emptySet()

        if (cachedValidators.isEmpty()) {
            cachedValidators =
                stargateTokenRepository.findAllDistinctValidatorIds().filterNotNull().toSet()
        }

        val removed = cachedValidators.minus(currentValidators)

        cachedValidators = currentValidators
        return removed
    }

    /** Extract validator IDs from exit events. */
    private fun findDelegationsFromExits(events: List<IndexedEvent>): List<String> =
        events
            .filter { it.eventType == "ValidatorExitRequested" }
            .mapNotNull { it.params.getAsString("validator") }
}
