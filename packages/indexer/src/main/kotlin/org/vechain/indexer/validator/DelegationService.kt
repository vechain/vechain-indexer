package org.vechain.indexer.validator

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.EventUtils.shouldProcessDelegationEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * DelegationService is responsible for managing the lifecycle of delegations.
 *
 * Each block, it:
 * 1. Finds delegations that are due for a status transition.
 * 2. Finds delegations referenced in chain events.
 * 3. Finds delegations belonging to validators that exited or disappeared.
 * 4. Applies lifecycle transitions (QUEUED -> ACTIVE -> EXITING -> EXITED).
 * 5. Applies event mutations (initiation, exit requests, withdrawals, rewards).
 * 6. Forces exit of delegations if their validator disappeared.
 *
 * Returns updated delegations and those that must be archived.
 */
@Profile("validator", "delegation")
@Service
open class DelegationService(
    private val repository: DelegationRepository,
    private val archiveService: ArchiveService<Delegation, DelegationArchive>,
    private val delegationPruner: TargetedPruner<Delegation, DelegationArchive>,
    private val validatorDelegationService: ValidatorDelegationService,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerSC: String,
) {
    private val logger = LoggerFactory.getLogger(DelegationService::class.java)
    private var cachedValidators: Set<String> = emptySet()

    open fun findById(id: String): Delegation? = repository.findByIdOrNull(id)

    open suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Delegation>, List<Delegation>> {
        val validatorSnapshots = validatorDelegationService.decodeValidatorSnapshots(callResponses)
        val disappeared = checkMissingValidators(validatorSnapshots)

        // 1. Collect delegations from different sources
        val (unknownStart, due) = findDueDelegations(block)
        val eventDelegations = findDelegationsFromEvents(events)
        val validatorExitDelegations = findDelegationsFromExits(events, disappeared)

        // 2. Build preload cache (deduped) and secondary index maps
        val allLoaded =
            (unknownStart + due + eventDelegations + validatorExitDelegations).associateBy { it.id }
        val preloaded = allLoaded.toMutableMap()
        val tokenIdToId = buildTokenIdIndex(allLoaded.values)
        val validatorToIds = buildValidatorIndex(allLoaded.values)

        // 3. Create accumulator with preload-backed findById
        val accumulator =
            VersionedDocumentAccumulator<Delegation>(
                findById = { id -> preloaded[id] ?: findById(id) },
                initialVersion = 0,
            )
        accumulator.startBlock()

        // 4. Process (same logical order as before)
        checkForUpdatesOnUnknown(unknownStart, block, validatorSnapshots, accumulator)
        applyScheduledTransitions(block, allLoaded.keys, accumulator)
        applyEventMutations(
            events,
            block,
            validatorSnapshots,
            accumulator,
            tokenIdToId,
            validatorToIds,
        )
        disappeared.forEach { handleValidatorDisappeared(it, block, accumulator, validatorToIds) }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<Delegation>, existing: List<Delegation>) {
        saveVersionedDocuments(updated, existing, repository, archiveService, delegationPruner)
    }

    // ------------------------------
    // Secondary index builders
    // ------------------------------

    private fun buildTokenIdIndex(delegations: Collection<Delegation>): MutableMap<String, String> =
        delegations
            .filter { it.status != Status.EXITED }
            .associateBy({ it.tokenId }, { it.id })
            .toMutableMap()

    private fun buildValidatorIndex(
        delegations: Collection<Delegation>
    ): Map<String, List<String>> =
        delegations
            .filter { it.status != Status.EXITED }
            .groupBy { it.validator }
            .mapValues { (_, dels) -> dels.map { it.id } }

    // ------------------------------
    // Step 1 helpers
    // ------------------------------

    private fun findDueDelegations(block: Block): Pair<List<Delegation>, List<Delegation>> {
        val delegations =
            repository.findByValidatorNextCycleInAndStatusIn(
                listOf(block.number, 0L),
                listOf(Status.QUEUED, Status.EXITING),
            )

        val zeroCycle = delegations.filter { it.validatorNextCycle == 0L }
        val nonZeroCycle = delegations - zeroCycle // everything else

        return zeroCycle to nonZeroCycle
    }

    private fun findDelegationsFromEvents(events: List<IndexedEvent>): List<Delegation> {
        val ids = events.mapNotNull { event -> event.params.getAsString("tokenId") }.distinct()
        return if (ids.isNotEmpty()) repository.findByTokenIdIn(ids).toList() else emptyList()
    }

    private fun findDelegationsFromExits(
        events: List<IndexedEvent>,
        disappeared: Set<String>,
    ): List<Delegation> {
        val exitingValidators =
            events
                .filter { it.eventType == "ValidatorExitRequested" }
                .mapNotNull { it.params.getAsString("validator") }

        return if (exitingValidators.isNotEmpty() || disappeared.isNotEmpty()) {
            repository.findByValidatorIn(exitingValidators + disappeared)
        } else {
            emptyList()
        }
    }

    // ------------------------------
    // Transitions
    // ------------------------------

    private fun applyScheduledTransitions(
        block: Block,
        candidateIds: Set<String>,
        accumulator: VersionedDocumentAccumulator<Delegation>,
    ) {
        candidateIds.forEach { id ->
            val (existing, nextVersion) = accumulator.resolve(id)
            if (
                existing != null &&
                    (existing.status == Status.QUEUED || existing.status == Status.EXITING) &&
                    existing.validatorNextCycle == block.number
            ) {
                accumulator.put(
                    id,
                    existing,
                    existing.copy(
                        status = validatorDelegationService.nextStatus(existing.status),
                        notify = true,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = nextVersion,
                    ),
                )
            }
        }
    }

    /**
     * Resolves delegations with unknown start blocks.
     *
     * Uses validator snapshots if available, otherwise queries the chain
     * (`getValidationPeriodDetails`). When a non-zero start block is found, the delegation is
     * updated with the new cycle info via the accumulator.
     */
    private suspend fun checkForUpdatesOnUnknown(
        unknown: List<Delegation>,
        block: Block,
        validatorsSnapshots: Map<String, ValidatorSnapshot>,
        accumulator: VersionedDocumentAccumulator<Delegation>,
    ) {
        if (unknown.isEmpty()) return

        val validators = unknown.groupBy { it.validator }

        val snapshotEmpty = validatorsSnapshots.isEmpty()
        val responses: List<InspectionResult> =
            (if (snapshotEmpty) {
                validatorDelegationService.fetchValidationPeriodDetails(validators.keys.toList())
            } else {
                emptyList()
            })

        validators.keys.forEachIndexed { index, validatorId ->
            val startBlock =
                if (snapshotEmpty) {
                    validatorDelegationService.determineStartBlock(responses[index])
                } else {
                    validatorsSnapshots[validatorId]?.startBlock ?: 0L
                }

            if (startBlock != 0L) {
                validators[validatorId]?.forEach { delegation ->
                    val (existing, nextVersion) = accumulator.resolve(delegation.id)
                    if (existing != null) {
                        accumulator.put(
                            delegation.id,
                            existing,
                            existing.copy(
                                validatorNextCycle = startBlock,
                                blockId = block.id,
                                blockNumber = block.number,
                                blockTimestamp = block.timestamp,
                                version = nextVersion,
                            ),
                        )
                    }
                }
            }
        }
    }

    // ------------------------------
    // Event mutations
    // ------------------------------

    private suspend fun applyEventMutations(
        events: List<IndexedEvent>,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        accumulator: VersionedDocumentAccumulator<Delegation>,
        tokenIdToId: MutableMap<String, String>,
        validatorToIds: Map<String, List<String>>,
    ) {
        events
            .filter { shouldProcessDelegationEvent(it, stakerSC) }
            .forEach { ev ->
                when (ev.eventType) {
                    "DelegationInitiated" ->
                        handleDelegationInitiated(
                            ev,
                            block,
                            validatorSnapshots,
                            accumulator,
                            tokenIdToId,
                        )
                    "DelegationExitRequested" ->
                        handleDelegationExitRequested(ev, block, accumulator)
                    "DelegationWithdrawn" -> handleDelegationWithdrawn(ev, block, accumulator)
                    "DelegationRewardsClaimed" ->
                        handleDelegationRewardsClaimed(ev, block, accumulator)
                    "Transfer" -> handleTransfer(ev, block, accumulator, tokenIdToId)
                    "ValidatorExitRequested" ->
                        handleValidatorExitRequested(
                            ev,
                            block,
                            validatorSnapshots,
                            accumulator,
                            validatorToIds,
                        )
                }
            }
    }

    // ------------------------------
    // Individual event handlers
    // ------------------------------

    /** Handles a new delegation being initiated. */
    private suspend fun handleDelegationInitiated(
        ev: IndexedEvent,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        accumulator: VersionedDocumentAccumulator<Delegation>,
        tokenIdToId: MutableMap<String, String>,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val tokenId = ev.params.getAsString("tokenId")!!
        val validator = ev.params.getAsString("validator")!!
        val (cycleLength, nextCycle) =
            validatorDelegationService.resolveCycleInfo(validator, block.number, validatorSnapshots)

        val (existing, nextVersion) = accumulator.resolve(delegationId)
        val newDelegation =
            Delegation(
                id = delegationId,
                validator = validator,
                tokenId = tokenId,
                tokenLevel = TokenLevel.fromOrdinal(ev.params.getAsString("levelId")!!.toInt())!!,
                status = Status.QUEUED,
                stakedAmount = ev.params.getAsString("amount")!!,
                totalRewardsClaimed = BigInteger.ZERO,
                owner = ev.origin!!,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = nextVersion,
                validatorNextCycle = nextCycle,
                validatorCycleLength = cycleLength,
                txId = ev.txId,
            )

        accumulator.put(delegationId, existing, newDelegation)
        tokenIdToId[tokenId] = delegationId
    }

    /** Handles a delegation requesting exit. */
    private fun handleDelegationExitRequested(
        ev: IndexedEvent,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Delegation>,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val (existing, nextVersion) = accumulator.resolve(delegationId)
        if (existing == null || existing.status == Status.EXITED) return

        accumulator.put(
            delegationId,
            existing,
            existing.copy(
                status = Status.EXITING,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                validatorNextCycle =
                    validatorDelegationService.resolveNextCycleBlock(
                        existing.validatorNextCycle,
                        existing.validatorCycleLength,
                        block.number,
                    ),
                version = nextVersion,
            ),
        )
    }

    /** Handles a delegation being withdrawn (final exit). */
    private fun handleDelegationWithdrawn(
        ev: IndexedEvent,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Delegation>,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val (existing, nextVersion) = accumulator.resolve(delegationId)
        if (existing == null) return

        accumulator.put(
            delegationId,
            existing,
            existing.copy(
                status = Status.EXITED,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = nextVersion,
            ),
        )
    }

    /** Handles a rewards claim on a delegation. */
    private fun handleDelegationRewardsClaimed(
        ev: IndexedEvent,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Delegation>,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val amount = ev.params.getAsBigInteger("amount")!!
        val (existing, nextVersion) = accumulator.resolve(delegationId)
        if (existing == null) return

        accumulator.put(
            delegationId,
            existing,
            existing.copy(
                totalRewardsClaimed = existing.totalRewardsClaimed + amount,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                blockId = block.id,
                version = nextVersion,
            ),
        )
    }

    /** Handles a token transfer -- O(1) lookup via tokenIdToId secondary index. */
    private fun handleTransfer(
        ev: IndexedEvent,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Delegation>,
        tokenIdToId: Map<String, String>,
    ) {
        val tokenId = ev.params.getAsString("tokenId") ?: return
        val to = ev.params.getAsString("to") ?: return
        val delegationId = tokenIdToId[tokenId] ?: return

        val (existing, nextVersion) = accumulator.resolve(delegationId)
        if (existing == null || existing.status == Status.EXITED) return

        accumulator.put(
            delegationId,
            existing,
            existing.copy(
                owner = to,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = nextVersion,
            ),
        )
    }

    /** Handles a validator requesting exit -- uses validatorToIds for O(1) lookup. */
    private suspend fun handleValidatorExitRequested(
        ev: IndexedEvent,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        accumulator: VersionedDocumentAccumulator<Delegation>,
        validatorToIds: Map<String, List<String>>,
    ) {
        val validatorId = ev.params.getAsString("validator")!!
        val exitAt =
            validatorDelegationService.getValidatorExitBlock(validatorId, validatorSnapshots)

        val delegationIds = validatorToIds[validatorId] ?: return
        delegationIds.forEach { id ->
            val (existing, nextVersion) = accumulator.resolve(id)
            if (existing == null || existing.status == Status.EXITED) return@forEach
            if (existing.status == Status.EXITING) return@forEach

            accumulator.put(
                id,
                existing,
                existing.copy(
                    status = Status.EXITING,
                    validatorNextCycle = exitAt,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = nextVersion,
                    force = true,
                ),
            )
        }
    }

    /** Handles a validator disappearing completely from chain state. */
    private fun handleValidatorDisappeared(
        validatorId: String,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Delegation>,
        validatorToIds: Map<String, List<String>>,
    ) {
        val delegationIds = validatorToIds[validatorId] ?: return
        delegationIds.forEach { id ->
            val (existing, nextVersion) = accumulator.resolve(id)
            if (existing == null || existing.status == Status.EXITED) return@forEach

            accumulator.put(
                id,
                existing,
                existing.copy(
                    status = Status.EXITED,
                    validatorNextCycle = block.number,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = nextVersion,
                    notify = true,
                    force = true,
                ),
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
            cachedValidators = repository.findValidatorIdsByStatusNot(Status.EXITED).toSet()
        }

        val removed = cachedValidators.minus(currentValidators)
        cachedValidators = currentValidators
        return removed
    }
}
