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
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
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
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.scheduler.EpochSeedProvider
import org.vechain.indexer.validator.scheduler.ThorSchedulerProcess

@Profile("validator", "stargate-token", "history")
@Service
open class ValidatorService(
    private val repository: ValidatorRepository,
    private val thorClient: ThorClient,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val epochSeedProvider: EpochSeedProvider,
    private val thorScheduler: ThorSchedulerProcess,
    private val networkDetectionService: NetworkDetectionService,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerAddress: String,
    @param:Value("\${indexer.start-block.validator}") private val validatorStartBlock: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val abiCache: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    private val detectedNetwork: VeChainNetwork by lazy {
        networkDetectionService.detectBlocking().network
    }

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
    ): Pair<List<Validator>, List<Validator>> {
        if (block.number < validatorStartBlock) return emptyList<Validator>() to emptyList()

        val existing = loadAll()
        val working = existing.toMutableMap()

        applyScheduledTransitions(block, working)
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
    open fun save(updates: List<Validator>, archive: List<Validator>) {
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
     * Drop the in-memory validator cache. Called from [ValidatorProcessor.resetProcessingState] on
     * rollback so the next block reloads from MongoDB instead of carrying state from a reorged-out
     * branch.
     */
    open fun invalidateCache() {
        validatorCache = null
    }

    /**
     * In-memory mirror of every persisted validator (including [Status.EXITED], which is terminal
     * but still receives `ValidationWithdrawn` cooldown-refund events that should decrement
     * `exitingVetStaked`). Loaded lazily on the first [loadAll] call after startup or invalidation;
     * kept in sync with MongoDB by [updateCache] after every successful [save]. The
     * single-thread-per-indexer model means no synchronization is needed.
     */
    private var validatorCache: MutableMap<String, Validator>? = null

    private fun loadAll(): Map<String, Validator> {
        val cached = validatorCache
        if (cached != null) return cached.toMap()
        val loaded = repository.findAll().associateByTo(mutableMapOf()) { it.id }
        validatorCache = loaded
        return loaded.toMap()
    }

    private fun updateCache(updates: List<Validator>) {
        val cache = validatorCache ?: return
        updates.forEach { u -> cache[u.id] = u }
    }

    // -------- Scheduled transitions --------

    /**
     * The chain transitions `Active+ExitBlock → Exit` silently at the period boundary — no event is
     * emitted. Detect it locally: any validator we have marked [Status.EXITING] whose `exitBlock`
     * has been reached has crossed into [Status.EXITED]. The next walk will confirm (chain returns
     * `status = 3`), but we apply it eagerly so mid-epoch reads see the right state and we don't
     * depend on the walk's periodicity.
     */
    private fun applyScheduledTransitions(block: Block, working: MutableMap<String, Validator>) {
        working.values.toList().forEach { v ->
            if (v.status != Status.EXITING) return@forEach
            val exitBlock = v.exitBlock ?: return@forEach
            if (block.number < exitBlock) return@forEach
            working[v.id] = v.copy(status = Status.EXITED)
        }
    }

    // -------- Mid-epoch event handling --------

    private fun applyEventChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
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
                            status = Status.EXITING,
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
        Validator(
            id = validatorId,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
        )

    private fun onValidationQueued(current: Validator, ev: IndexedEvent): Validator {
        val endorser = ev.params.getAsString("endorser")?.lowercase()
        val period = ev.params.getAsBigInteger("period")?.toLong()
        val stakeVet = ev.params.getAsBigInteger("stake")?.let(NumberUtils::toVET)
        return current.copy(
            status = Status.QUEUED,
            endorser = endorser ?: current.endorser,
            cyclePeriodLength = period ?: current.cyclePeriodLength,
            validatorQueuedVetStaked = stakeVet ?: current.validatorQueuedVetStaked,
        )
    }

    /**
     * Endorser called `withdrawStake`. Two cases the chain handles distinctly:
     * 1. **Queued → Exit.** When the validator is still [Status.QUEUED], the native side
     *    transitions them straight to [Status.EXITED] and removes them from the queue. We mirror
     *    that here using our cached status — no other field changes (queued stake has been zeroed
     *    on the chain side, but that's the next walk's job to reconcile).
     * 2. **Active / Exit refund.** For any other status, `withdrawStake` just pays out cooldown or
     *    pending-unlock VET; the validator stays in whatever state they were in. Decrement the
     *    exiting buckets so mid-epoch reads stay accurate; the walk re-reads `exitingVET` from the
     *    chain each epoch and overwrites `exitingVetStaked`. `validatorExitingVetStaked` is only
     *    ever set by [ValidationSignaledExit] snapshots, so the `.max(BigDecimal.ZERO)` clamp keeps
     *    it sane when `withdrawStake` follows a bare `decreaseStake` (no prior signalExit).
     */
    private fun onValidationWithdrawn(current: Validator, ev: IndexedEvent): Validator {
        if (current.status == Status.QUEUED) {
            // Chain zeroes the validator's own `QueuedVET` and removes them from the queue;
            // delegator queued stake is untouched. The walk won't revisit off-list validators,
            // so clear queue-only fields here or the stale data will persist forever.
            val ownQueued = current.validatorQueuedVetStaked ?: BigDecimal.ZERO
            return current.copy(
                status = Status.EXITED,
                validatorQueuedVetStaked = BigDecimal.ZERO,
                queuedVetStaked =
                    (current.queuedVetStaked ?: BigDecimal.ZERO)
                        .subtract(ownQueued)
                        .max(BigDecimal.ZERO),
                queuePosition = null,
                availableStartBlock = null,
            )
        }
        val stakeVet = ev.params.getAsBigInteger("stake")?.let(NumberUtils::toVET) ?: return current
        return current.copy(
            exitingVetStaked =
                (current.exitingVetStaked ?: BigDecimal.ZERO)
                    .subtract(stakeVet)
                    .max(BigDecimal.ZERO),
            validatorExitingVetStaked =
                (current.validatorExitingVetStaked ?: BigDecimal.ZERO)
                    .subtract(stakeVet)
                    .max(BigDecimal.ZERO),
        )
    }

    private fun onStakeIncreased(current: Validator, ev: IndexedEvent): Validator {
        val addedVet = ev.params.getAsBigInteger("added")?.let(NumberUtils::toVET) ?: return current
        // Direction depends on the validator's current lifecycle stage. Queued validators have not
        // entered the locked pool yet; everyone else increases their locked stake. The epoch walk
        // will reconcile any drift.
        return if (current.status == Status.QUEUED) {
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

    private fun onStakeDecreased(current: Validator, ev: IndexedEvent): Validator {
        val removedVet =
            ev.params.getAsBigInteger("removed")?.let(NumberUtils::toVET) ?: return current
        return if (current.status == Status.QUEUED) {
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
     * On a custom single-proposer network (Thor solo) we credit the lone signer with one
     * scheduled-and-proposed slot per block — no VRF schedule exists to reconstruct.
     *
     * Otherwise, if [epochSeedProvider] yields a seed we reconstruct the deterministic PoS schedule
     * and attribute scheduled / proposed / missed slots precisely. Without a seed (current default,
     * until Beta extraction via VRF Verify is implemented), we fall back to bumping only
     * `proposedBlocks` for the actual signer; `scheduledSlots` and `missedSlots` stay at zero so
     * the reader can tell attribution wasn't running.
     */
    private suspend fun updateLiveness(block: Block, working: MutableMap<String, Validator>) {
        val signer = block.signer.lowercase()

        // Thor solo (and any custom single-proposer network) has no VRF schedule to
        // reconstruct: the sole ACTIVE validator is the scheduled proposer for every block.
        // Credit one scheduled-and-proposed slot to the signer so liveness reflects reality
        // instead of being stuck at the seed-missing fallback (scheduledSlots = 0).
        if (isSoloLikeNetwork(working)) {
            val current = working[signer] ?: return
            working[signer] =
                current.copy(
                    scheduledSlots = current.scheduledSlots + 1,
                    proposedBlocks = current.proposedBlocks + 1,
                    lastProposedBlockNumber = block.number,
                )
            return
        }

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
                // EXITING validators are still in the staker's active list and producing blocks
                // until `exitBlock`; include them so VRF-schedule reconstruction matches the
                // chain's actual leader group.
                .filter { it.status == Status.ACTIVE || it.status == Status.EXITING }
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
            if (isActualSlot) {
                val scheduled = working[scheduledId]
                if (scheduled != null) {
                    working[scheduledId] =
                        scheduled.copy(scheduledSlots = scheduled.scheduledSlots + 1)
                }
                // Trust block.signer over the reconstructed schedule for proposal attribution;
                // they can diverge if our active-set view lags the chain.
                val proposerId = if (working.containsKey(signer)) signer else scheduledId
                val proposer = working[proposerId] ?: return@repeat
                working[proposerId] =
                    proposer.copy(
                        proposedBlocks = proposer.proposedBlocks + 1,
                        lastProposedBlockNumber = block.number,
                    )
            } else {
                val current = working[scheduledId] ?: return@repeat
                working[scheduledId] =
                    current.copy(
                        scheduledSlots = current.scheduledSlots + 1,
                        missedSlots = current.missedSlots + 1,
                        lastMissedBlockNumber = block.number,
                    )
            }
        }
    }

    private fun isSoloLikeNetwork(working: Map<String, Validator>): Boolean {
        if (detectedNetwork != VeChainNetwork.CUSTOM) return false
        // EXITING is still actively producing until `exitBlock` — count it as part of the leader
        // group, otherwise a solo validator that signals exit would lose attribution entirely.
        return working.values.count { it.status == Status.ACTIVE || it.status == Status.EXITING } ==
            1
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
     * Validators present in our working set but absent from both lists are left untouched — they're
     * either [Status.EXITED] (chain still tracks them via `getValidation`, just not in the iterator
     * lists) or [Status.EXITING] waiting for their scheduled flip. Neither needs the walk's
     * reconciliation.
     */
    private suspend fun walkStakerState(block: Block, working: MutableMap<String, Validator>) {
        val revision = BlockRevision.Id(block.id)
        val activeIds = walkList("firstActive", block, revision)
        val queuedIds = walkList("firstQueued", block, revision)
        val onChainIds = activeIds + queuedIds

        if (onChainIds.isEmpty()) {
            logger.debug("Staker returned no validators at block {}", block.number)
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
        requireStakerResponseCount(
            "validator state walk",
            responses,
            perValidatorClauses.size,
            block,
        )

        onChainIds.forEachIndexed { index, validatorId ->
            val base = index * 3
            val validationAbi = getAbi("getValidation")
            val periodAbi = getAbi("getValidationPeriodDetails")
            val totalsAbi = getAbi("getValidationTotals")
            val validation =
                decodeStakerResponse("getValidation", responses[base], validationAbi, block)
            val period =
                decodeStakerResponse(
                    "getValidationPeriodDetails",
                    responses[base + 1],
                    periodAbi,
                    block,
                )
            val totals =
                decodeStakerResponse("getValidationTotals", responses[base + 2], totalsAbi, block)

            val current = working[validatorId] ?: newDoc(validatorId, block)
            val validatorVetStaked = (validation["stake"] as BigInteger)
            val lockedVet = (totals["lockedVET"] as BigInteger)
            val exitBlockRaw = (period["exitBlock"] as BigInteger).toLong()
            val hasExitBlock = exitBlockRaw in 1L until MAX_UINT32_LONG
            val rawStatus = Status.fromCode((validation["status"] as BigInteger).toInt())
            // The chain has no `Exiting` enum value — it's `Active + ExitBlock!=nil`. Re-derive so
            // the indexer's EXITING label survives the walk instead of being clobbered to ACTIVE.
            val derivedStatus =
                if (rawStatus == Status.ACTIVE && hasExitBlock) Status.EXITING else rawStatus
            working[validatorId] =
                current.copy(
                    endorser = (validation["endorser"] as String).lowercase(),
                    status = derivedStatus,
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
                    exitBlock = exitBlockRaw.takeIf { hasExitBlock },
                    completedPeriods = (period["completedPeriods"] as BigInteger).toLong(),
                    queuePosition = queuePositionById[validatorId],
                    availableStartBlock = null,
                )
        }

        // Pair queued validators with the earliest exit blocks to estimate when each can
        // activate: prefer the chain's own startBlock if set, otherwise the k-th queued
        // validator inherits the k-th earliest exit.
        val exitingBlocks =
            working.values
                .filter { it.status == Status.EXITING }
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
    }

    private suspend fun walkList(
        headFn: String,
        block: Block,
        revision: BlockRevision,
    ): List<String> {
        val ids = mutableListOf<String>()
        val headAbi = getAbi(headFn)
        val nextAbi = getAbi("next")
        val firstResp =
            requireSingleStakerResponse(
                headFn,
                thorClient.inspectClauses(
                    listOf(ContractUtils.createClause(stakerAddress, headAbi)),
                    revision,
                ),
                block,
            )
        var current =
            (decodeStakerResponse(headFn, firstResp, headAbi, block)[headFn] as String).lowercase()
        while (!Address(current).isZero()) {
            ids.add(current)
            val nextResp =
                requireSingleStakerResponse(
                    "next",
                    thorClient.inspectClauses(
                        listOf(
                            ContractUtils.createClause(
                                stakerAddress,
                                nextAbi,
                                AddressUtils.toBigInt(current),
                            )
                        ),
                        revision,
                    ),
                    block,
                )
            current =
                (decodeStakerResponse("next", nextResp, nextAbi, block)["nextValidation"] as String)
                    .lowercase()
        }
        return ids
    }

    private fun requireSingleStakerResponse(
        functionName: String,
        responses: List<InspectionResult>,
        block: Block,
    ): InspectionResult {
        requireStakerResponseCount(functionName, responses, 1, block)
        return responses.first()
    }

    private fun requireStakerResponseCount(
        functionName: String,
        responses: List<InspectionResult>,
        expected: Int,
        block: Block,
    ) {
        if (responses.size == expected) return
        throw IllegalStateException(
            "Built-in staker inspect call '$functionName' returned ${responses.size} " +
                "response(s), expected $expected at block ${block.number} (${block.id}) for " +
                "contract $stakerAddress. Check INDEXER_START_BLOCK_VALIDATOR, THOR_URL, and " +
                "BUILTIN_STAKER_CONTRACT."
        )
    }

    private fun decodeStakerResponse(
        functionName: String,
        response: InspectionResult,
        abi: AbiElement,
        block: Block,
    ): Map<String, Any?> {
        val cleanData = response.data.removePrefix("0x")
        val expectedMinHexLength = abi.outputs.size * ABI_WORD_HEX_LENGTH
        if (
            response.reverted ||
                !response.vmError.isNullOrBlank() ||
                cleanData.length < expectedMinHexLength
        ) {
            throw stakerInspectFailure(functionName, response, block, expectedMinHexLength)
        }

        return try {
            FunctionReturnDecoder.decode(response.data, abi.outputs)
        } catch (ex: RuntimeException) {
            throw stakerInspectFailure(functionName, response, block, expectedMinHexLength, ex)
        }
    }

    private fun stakerInspectFailure(
        functionName: String,
        response: InspectionResult,
        block: Block,
        expectedMinHexLength: Int,
        cause: RuntimeException? = null,
    ): IllegalStateException {
        val cleanData = response.data.removePrefix("0x")
        val reason =
            when {
                response.reverted -> "reverted"
                !response.vmError.isNullOrBlank() -> "failed with VM error '${response.vmError}'"
                cleanData.isBlank() -> "returned no ABI data"
                else ->
                    "returned malformed ABI data (${cleanData.length} hex chars, expected at " +
                        "least $expectedMinHexLength)"
            }
        return IllegalStateException(
            "Built-in staker inspect call '$functionName' $reason at block ${block.number} " +
                "(${block.id}) for contract $stakerAddress. Check INDEXER_START_BLOCK_VALIDATOR, " +
                "THOR_URL, and BUILTIN_STAKER_CONTRACT; this usually means the validator indexer " +
                "is reading a block before the built-in staker exists, or it is pointed at the " +
                "wrong network/contract.",
            cause,
        )
    }

    private fun getAbi(name: String): AbiElement =
        abiCache.getOrPut(name) {
            AbiLoader.loadFunctions("abis/stargate", listOf(name)).firstOrNull { it.name == name }
                ?: throw IllegalStateException("Function '$name' not found in staker ABI")
        }

    // -------- Diff & versioning --------

    private fun diff(
        existing: Map<String, Validator>,
        working: Map<String, Validator>,
        block: Block,
    ): List<Validator> =
        working.values
            .map { it.withDerivedFields() }
            .mapNotNull { carried ->
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

    private fun Validator.withDerivedFields(): Validator =
        copy(
            vetStaked =
                (validatorVetStaked ?: BigDecimal.ZERO).add(delegatorVetStaked ?: BigDecimal.ZERO)
        )

    private fun archiveDocsForUpdates(
        existing: Map<String, Validator>,
        updates: List<Validator>,
    ): List<Validator> = updates.mapNotNull { existing[it.id] }

    private fun isEpochBoundary(blockNumber: Long): Boolean = blockNumber % EPOCH_LENGTH == 0L

    companion object {
        private const val EPOCH_LENGTH = 180L
        private const val BLOCK_INTERVAL_SECONDS = 10L
        private const val ABI_WORD_HEX_LENGTH = 64
        private const val MAX_UINT32_LONG = 4294967295L
    }
}
