package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.scheduler.EpochSeedProvider
import org.vechain.indexer.validator.scheduler.ThorSchedulerProcess

@Profile("validator-v2", "validator")
@Service
open class ValidatorV2Service(
    private val repository: ValidatorV2Repository,
    private val thorClient: ThorClient,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val epochSeedProvider: EpochSeedProvider,
    private val thorScheduler: ThorSchedulerProcess,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerAddress: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val abiCache: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    @Volatile private var lastProcessedBlock: Block? = null

    /**
     * Builds the working set for [block]:
     * 1. apply mid-epoch event-driven mutations (status, stake, beneficiary)
     * 2. bump cumulative liveness counters from the block header
     * 3. on the first block we see, and at every epoch boundary, walk the built-in staker contract
     *    to reconcile durable state (status, stakes, periods)
     */
    open suspend fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
    ): Pair<List<ValidatorV2>, List<ValidatorV2>> {
        val existing = loadActive()
        val working = existing.toMutableMap()

        applyEventChanges(matchedEvents, working, block)
        updateLiveness(block, working)

        if (existing.isEmpty() || isEpochBoundary(block.number)) {
            walkStakerState(block, working)
        }

        val updates = diff(existing, working, block)
        lastProcessedBlock = block
        return updates to archiveDocsForUpdates(existing, updates)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<ValidatorV2>, archive: List<ValidatorV2>) {
        saveVersionedDocuments(
            updates,
            archive,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
            inlineVersioningProperties.minVersions,
        )
        updateCache(updates)
    }

    /**
     * Drop the in-memory active-validator cache. Called from
     * [ValidatorV2Processor.resetProcessingState] on rollback so the next block reloads from
     * MongoDB instead of carrying state from a reorged-out branch.
     */
    open fun invalidateCache() {
        activeCache = null
    }

    /**
     * In-memory mirror of the persisted active-validator set. Loaded lazily on the first
     * [loadActive] call after startup or invalidation; kept in sync with MongoDB by [updateCache]
     * after every successful [save]. The single-thread-per-indexer model means no synchronization
     * is needed.
     */
    private var activeCache: MutableMap<String, ValidatorV2>? = null

    private fun loadActive(): Map<String, ValidatorV2> {
        val cached = activeCache
        if (cached != null) return cached.toMap()
        val loaded =
            repository.findByStatusNot(StatusV2.WITHDRAWN).associateByTo(mutableMapOf()) { it.id }
        activeCache = loaded
        return loaded.toMap()
    }

    private fun updateCache(updates: List<ValidatorV2>) {
        val cache = activeCache ?: return
        updates.forEach { u ->
            if (u.status == StatusV2.WITHDRAWN) {
                cache.remove(u.id)
            } else {
                cache[u.id] = u
            }
        }
    }

    // -------- Mid-epoch event handling --------

    private fun applyEventChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, ValidatorV2>,
        block: Block,
    ) {
        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator")?.lowercase() ?: return@forEach
            val current = working[validatorId] ?: newDoc(validatorId, block)
            val next =
                when (ev.eventType) {
                    "ValidationQueued" -> onValidationQueued(current, ev)
                    "ValidationSignaledExit" ->
                        // Snapshot the validator's own locked stake — that's the portion
                        // that's now exiting. `exitingVetStaked` (from the walk) carries
                        // both validator + delegator exits; `delegatorExitingStake` is
                        // derived as the difference at API time.
                        current.copy(
                            status = StatusV2.EXITING,
                            validatorExitingVetStaked = current.validatorVetStaked,
                        )
                    "ValidationWithdrawn" -> onValidationWithdrawn(current, ev)
                    "StakeIncreased" -> onStakeIncreased(current, ev)
                    "StakeDecreased" -> onStakeDecreased(current, ev)
                    "BeneficiarySet" ->
                        current.copy(
                            beneficiary = ev.params.getAsString("beneficiary")?.lowercase()
                        )
                    else -> {
                        logger.debug(
                            "Ignoring event {} for validator {}",
                            ev.eventType,
                            validatorId,
                        )
                        current
                    }
                }
            working[validatorId] = next
        }
    }

    private fun newDoc(validatorId: String, block: Block) =
        ValidatorV2(
            id = validatorId,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
        )

    private fun onValidationQueued(current: ValidatorV2, ev: IndexedEvent): ValidatorV2 {
        val endorser = ev.params.getAsString("endorser")?.lowercase()
        val period = ev.params.getAsBigInteger("period")?.toLong()
        val stakeVet = ev.params.getAsBigInteger("stake")?.let(NumberUtils::toVET)
        return current.copy(
            status = StatusV2.QUEUED,
            endorser = endorser ?: current.endorser,
            cyclePeriodLength = period ?: current.cyclePeriodLength,
            validatorQueuedVetStaked = stakeVet ?: current.validatorQueuedVetStaked,
        )
    }

    private fun onValidationWithdrawn(current: ValidatorV2, ev: IndexedEvent): ValidatorV2 {
        val stakeVet =
            ev.params.getAsBigInteger("stake")?.let(NumberUtils::toVET) ?: BigDecimal.ZERO
        // After withdrawal the validator no longer exists in the staker; zero out volatile stakes.
        return current.copy(
            status = StatusV2.WITHDRAWN,
            validatorVetStaked = BigDecimal.ZERO,
            validatorLockedWeight = BigDecimal.ZERO,
            validatorQueuedVetStaked = BigDecimal.ZERO,
            delegatorVetStaked = BigDecimal.ZERO,
            queuedVetStaked = BigDecimal.ZERO,
            exitingVetStaked =
                (current.exitingVetStaked ?: BigDecimal.ZERO)
                    .subtract(stakeVet)
                    .max(BigDecimal.ZERO),
            validatorExitingVetStaked = BigDecimal.ZERO,
            totalNextPeriodWeight = BigDecimal.ZERO,
            queuePosition = null,
            availableStartBlock = null,
        )
    }

    private fun onStakeIncreased(current: ValidatorV2, ev: IndexedEvent): ValidatorV2 {
        val addedVet = ev.params.getAsBigInteger("added")?.let(NumberUtils::toVET) ?: return current
        // Direction depends on the validator's current lifecycle stage. Queued validators have not
        // entered the locked pool yet; everyone else increases their locked stake. The epoch walk
        // will reconcile any drift.
        return if (current.status == StatusV2.QUEUED) {
            current.copy(
                validatorQueuedVetStaked =
                    (current.validatorQueuedVetStaked ?: BigDecimal.ZERO).add(addedVet)
            )
        } else {
            current.copy(
                validatorVetStaked = (current.validatorVetStaked ?: BigDecimal.ZERO).add(addedVet)
            )
        }
    }

    private fun onStakeDecreased(current: ValidatorV2, ev: IndexedEvent): ValidatorV2 {
        val removedVet =
            ev.params.getAsBigInteger("removed")?.let(NumberUtils::toVET) ?: return current
        return if (current.status == StatusV2.QUEUED) {
            current.copy(
                validatorQueuedVetStaked =
                    (current.validatorQueuedVetStaked ?: BigDecimal.ZERO)
                        .subtract(removedVet)
                        .max(BigDecimal.ZERO)
            )
        } else {
            current.copy(
                validatorVetStaked =
                    (current.validatorVetStaked ?: BigDecimal.ZERO)
                        .subtract(removedVet)
                        .max(BigDecimal.ZERO)
            )
        }
    }

    // -------- Liveness from block headers --------

    /**
     * Cumulative liveness counters derived from block headers alone — no state reads.
     *
     * If [epochSeedProvider] yields a seed we reconstruct the deterministic PoS schedule and
     * attribute scheduled / proposed / missed slots precisely. Without a seed (current default,
     * until Beta extraction via VRF Verify is implemented), we fall back to bumping only
     * `proposedBlocks` for the actual signer; `scheduledBlocks` and `missedBlocks` stay at zero so
     * the reader can tell attribution wasn't running.
     */
    private suspend fun updateLiveness(block: Block, working: MutableMap<String, ValidatorV2>) {
        val signer = block.signer.lowercase()
        val seed = epochSeedProvider.seedFor(block)

        if (seed == null) {
            val current = working[signer] ?: return
            working[signer] =
                current.copy(
                    proposedBlocks = current.proposedBlocks + 1,
                    lastProposedBlockNumber = block.number,
                )
            return
        }

        val proposers =
            working.values
                .filter { it.status == StatusV2.ACTIVE }
                .map {
                    ThorSchedulerProcess.Proposer(
                        address = it.id,
                        weight = (it.validatorLockedWeight ?: BigDecimal.ZERO).toLong(),
                        active = true,
                    )
                }
        if (proposers.isEmpty()) return

        val schedule =
            thorScheduler.schedule(
                seedHex = "0x" + seed.joinToString("") { "%02x".format(it) },
                parentBlockNumber = block.number - 1,
                proposers = proposers,
            )
        if (schedule.isEmpty()) return

        val parentTimestamp = parentTimestampFor(block)
        val slotsElapsed = ((block.timestamp - parentTimestamp) / BLOCK_INTERVAL_SECONDS).toInt()
        if (slotsElapsed <= 0) return

        repeat(slotsElapsed) { k ->
            val scheduledId = schedule[k % schedule.size]
            val isActualSlot = k == slotsElapsed - 1
            val current = working[scheduledId] ?: return@repeat
            working[scheduledId] =
                if (isActualSlot) {
                    current.copy(
                        scheduledBlocks = current.scheduledBlocks + 1,
                        proposedBlocks = current.proposedBlocks + 1,
                        lastProposedBlockNumber = block.number,
                    )
                } else {
                    current.copy(
                        scheduledBlocks = current.scheduledBlocks + 1,
                        missedBlocks = current.missedBlocks + 1,
                        lastMissedBlockNumber = block.number,
                    )
                }
        }
    }

    private suspend fun parentTimestampFor(block: Block): Long {
        val cached = lastProcessedBlock
        if (cached != null && cached.id == block.parentID) return cached.timestamp
        return thorClient.getBlockUnexpanded(BlockRevision.Id(block.parentID)).timestamp
    }

    // -------- Epoch-boundary state walk --------

    /**
     * Walks the built-in staker contract directly (no helper contract) to reconcile durable state.
     *
     * Mirrors `GetValidators.sol`:
     * 1. firstActive → next → ... → 0x0 (active set, in order)
     * 2. firstQueued → next → ... → 0x0 (queued set, in order)
     * 3. for each id: getValidation, getValidationPeriodDetails, getValidationTotals (batched)
     *
     * Validators present in our working set but absent from both lists are treated as withdrawn.
     */
    private suspend fun walkStakerState(block: Block, working: MutableMap<String, ValidatorV2>) {
        val revision = BlockRevision.Id(block.id)
        val activeIds = walkList("firstActive", revision)
        val queuedIds = walkList("firstQueued", revision)
        val onChainIds = activeIds + queuedIds

        if (onChainIds.isEmpty()) {
            logger.debug("Staker returned no validators at block {}", block.number)
            // Anyone we knew about that's no longer on chain has been withdrawn.
            markMissingAsWithdrawn(working, emptySet())
            return
        }

        val queuePositionById = queuedIds.withIndex().associate { (i, id) -> id to (i + 1L) }

        // Three clauses per validator: getValidation, getValidationPeriodDetails,
        // getValidationTotals.
        val perValidatorClauses =
            onChainIds.flatMap { validatorId ->
                listOf(
                    ContractUtils.createClause(
                        stakerAddress,
                        getAbi("getValidation"),
                        AddressUtils.toBigInt(validatorId),
                    ),
                    ContractUtils.createClause(
                        stakerAddress,
                        getAbi("getValidationPeriodDetails"),
                        AddressUtils.toBigInt(validatorId),
                    ),
                    ContractUtils.createClause(
                        stakerAddress,
                        getAbi("getValidationTotals"),
                        AddressUtils.toBigInt(validatorId),
                    ),
                )
            }
        val responses = thorClient.inspectClauses(perValidatorClauses, revision)

        onChainIds.forEachIndexed { index, validatorId ->
            val base = index * 3
            val validation =
                FunctionReturnDecoder.decode(responses[base].data, getAbi("getValidation").outputs)
            val period =
                FunctionReturnDecoder.decode(
                    responses[base + 1].data,
                    getAbi("getValidationPeriodDetails").outputs,
                )
            val totals =
                FunctionReturnDecoder.decode(
                    responses[base + 2].data,
                    getAbi("getValidationTotals").outputs,
                )

            val current = working[validatorId] ?: newDoc(validatorId, block)
            val validatorVetStaked = (validation["stake"] as BigInteger)
            val lockedVet = (totals["lockedVET"] as BigInteger)
            val exitBlockRaw = (period["exitBlock"] as BigInteger).toLong()
            working[validatorId] =
                current.copy(
                    endorser = (validation["endorser"] as String).lowercase(),
                    status = StatusV2.fromCode((validation["status"] as BigInteger).toInt()),
                    validatorVetStaked = NumberUtils.toVET(validatorVetStaked),
                    validatorLockedWeight = NumberUtils.toVET(validation["weight"] as BigInteger),
                    validatorQueuedVetStaked =
                        NumberUtils.toVET(validation["queuedStake"] as BigInteger),
                    delegatorVetStaked =
                        NumberUtils.toVET((lockedVet - validatorVetStaked).max(BigInteger.ZERO)),
                    queuedVetStaked = NumberUtils.toVET(totals["queuedVET"] as BigInteger),
                    exitingVetStaked = NumberUtils.toVET(totals["exitingVET"] as BigInteger),
                    totalNextPeriodWeight =
                        NumberUtils.toVET(totals["nextPeriodWeight"] as BigInteger),
                    cyclePeriodLength = (period["period"] as BigInteger).toLong(),
                    startBlock = (period["startBlock"] as BigInteger).toLong(),
                    exitBlock = exitBlockRaw.takeIf { it in 1L until MAX_UINT32_LONG },
                    completedPeriods = (period["completedPeriods"] as BigInteger).toLong(),
                    queuePosition = queuePositionById[validatorId],
                    availableStartBlock = null,
                )
        }

        // Pair queued validators with the earliest exit blocks to estimate when each can
        // activate. Mirrors V1's `ValidatorAssembler.calculateQueueInfo`: prefer the chain's
        // own startBlock if set, otherwise the k-th queued validator inherits the k-th
        // earliest exit.
        val exitingBlocks =
            working.values
                .filter { it.status == StatusV2.EXITING }
                .mapNotNull { it.exitBlock }
                .sorted()

        queuedIds.forEachIndexed { qIndex, validatorId ->
            val current = working[validatorId] ?: return@forEachIndexed
            val cStart = current.startBlock
            val availableStart =
                when {
                    cStart != null && cStart > 0L -> cStart
                    qIndex < exitingBlocks.size -> exitingBlocks[qIndex]
                    else -> 0L
                }
            working[validatorId] = current.copy(availableStartBlock = availableStart)
        }

        markMissingAsWithdrawn(working, onChainIds.toSet())
    }

    private fun markMissingAsWithdrawn(
        working: MutableMap<String, ValidatorV2>,
        onChainIds: Set<String>,
    ) {
        val snapshot = working.toMap()
        snapshot.forEach { (id, doc) ->
            if (id !in onChainIds && doc.status != StatusV2.WITHDRAWN) {
                working[id] = doc.copy(status = StatusV2.WITHDRAWN)
            }
        }
    }

    private suspend fun walkList(headFn: String, revision: BlockRevision): List<String> {
        val ids = mutableListOf<String>()
        val headAbi = getAbi(headFn)
        val nextAbi = getAbi("next")
        val firstResp =
            thorClient
                .inspectClauses(
                    listOf(ContractUtils.createClause(stakerAddress, headAbi)),
                    revision,
                )
                .first()
        var current =
            (FunctionReturnDecoder.decode(firstResp.data, headAbi.outputs)[headFn] as String)
                .lowercase()
        while (!Address(current).isZero()) {
            ids.add(current)
            val nextResp =
                thorClient
                    .inspectClauses(
                        listOf(
                            ContractUtils.createClause(
                                stakerAddress,
                                nextAbi,
                                AddressUtils.toBigInt(current),
                            )
                        ),
                        revision,
                    )
                    .first()
            current =
                (FunctionReturnDecoder.decode(nextResp.data, nextAbi.outputs)["nextValidation"]
                        as String)
                    .lowercase()
        }
        return ids
    }

    private fun getAbi(name: String): AbiElement =
        abiCache.getOrPut(name) {
            AbiLoader.loadFunctions("abis/stargate", listOf(name)).firstOrNull { it.name == name }
                ?: throw IllegalStateException("Function '$name' not found in staker ABI")
        }

    // -------- Diff & versioning --------

    private fun diff(
        existing: Map<String, ValidatorV2>,
        working: Map<String, ValidatorV2>,
        block: Block,
    ): List<ValidatorV2> =
        working.values.mapNotNull { carried ->
            val prior = existing[carried.id]
            if (carried == prior) null
            else
                carried.copy(
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = (prior?.version ?: 0) + 1,
                )
        }

    private fun archiveDocsForUpdates(
        existing: Map<String, ValidatorV2>,
        updates: List<ValidatorV2>,
    ): List<ValidatorV2> = updates.mapNotNull { existing[it.id] }

    private fun isEpochBoundary(blockNumber: Long): Boolean = blockNumber % EPOCH_LENGTH == 0L

    companion object {
        private const val EPOCH_LENGTH = 180L
        private const val BLOCK_INTERVAL_SECONDS = 10L
        private const val MAX_UINT32_LONG = 4294967295L
    }
}
