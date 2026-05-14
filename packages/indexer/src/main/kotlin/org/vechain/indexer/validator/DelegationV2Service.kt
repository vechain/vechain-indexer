package org.vechain.indexer.validator

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * V2 delegation indexer.
 *
 * Pure event-driven. Reads `ValidatorV2` from MongoDB (via [ValidatorV2Repository]) for cycle math
 * — no chain calls, no aggregator dependency, no `callDataClauses`. Ordering with the V2 validator
 * indexer is handled by `dependsOn(validatorV2Indexer)` in [DelegationV2Config].
 *
 * Each block:
 * 1. Load three sets of candidates: (a) due-this-block, (b) zero-cycle (validator hasn't started
 *    yet), (c) any delegation/validator touched by this block's events.
 * 2. Apply scheduled transitions for due delegations (QUEUED→ACTIVE, EXITING→EXITED).
 * 3. Refresh `transitionAtBlock` on zero-cycle delegations whose validator has since activated.
 * 4. Apply event mutations.
 * 5. Diff against prior state and emit only changed documents.
 */
@Profile("delegation-v2", "delegation")
@Service
open class DelegationV2Service(
    private val repository: DelegationV2Repository,
    private val validatorRepository: ValidatorV2Repository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerSC: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    open suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<DelegationV2>, List<DelegationV2>> {
        val due =
            repository.findByTransitionAtBlockAndStatusIn(
                block.number,
                listOf(DelegationStatusV2.QUEUED, DelegationStatusV2.EXITING),
            )
        val zeroCycle = loadZeroCycle()

        val tokenIds = events.mapNotNull { it.params.getAsString("tokenId") }.distinct()
        val byTokenId =
            if (tokenIds.isNotEmpty()) repository.findByTokenIdIn(tokenIds) else emptyList()

        val touchedValidatorIds =
            events
                .filter {
                    (it.eventType == "ValidationSignaledExit" ||
                        it.eventType == "ValidationWithdrawn") &&
                        it.address.equals(stakerSC, ignoreCase = true)
                }
                .mapNotNull { it.params.getAsString("validator")?.lowercase() }
                .distinct()
        val byValidator =
            if (touchedValidatorIds.isNotEmpty()) repository.findByValidatorIn(touchedValidatorIds)
            else emptyList()

        val existing = (due + zeroCycle + byTokenId + byValidator).associateBy { it.id }
        val working = existing.toMutableMap()
        val tokenIdToId: MutableMap<String, String> =
            working.values.associateBy({ it.tokenId }, { it.id }).toMutableMap()
        val validatorToIds: MutableMap<String, MutableList<String>> =
            working.values
                .filter { it.status != DelegationStatusV2.EXITED }
                .groupBy { it.validator }
                .mapValues { (_, dels) -> dels.map { it.id }.toMutableList() }
                .toMutableMap()

        val validators = preloadValidators(working.values, events)

        applyScheduledTransitions(block, working)
        refreshZeroCycle(block, working, validators)
        events.forEach { ev ->
            applyEventMutation(ev, block, working, tokenIdToId, validatorToIds, validators)
        }

        val updates =
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
        val archive = updates.mapNotNull { existing[it.id] }
        return updates to archive
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<DelegationV2>, archive: List<DelegationV2>) {
        saveVersionedDocuments(
            updates,
            archive,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
            inlineVersioningProperties.minVersions,
        )
        updateZeroCycleCache(updates)
    }

    // ------------------------------ zero-cycle cache ------------------------------

    /**
     * In-memory mirror of zero-cycle delegations (status QUEUED with no scheduled transition —
     * waiting for their validator to gain a `startBlock`). Loaded lazily; kept in sync with MongoDB
     * by [updateZeroCycleCache] after every successful [save]. Cleared via [invalidateCache] on
     * rollback (wired through `DelegationV2Processor.resetProcessingState`).
     *
     * Cached because the set rarely changes block-to-block but the query is unbounded — V1 took the
     * same shortcut for the same reason.
     */
    private var zeroCycleCache: MutableMap<String, DelegationV2>? = null

    /** Drop the zero-cycle cache. Invoked on rollback so the next block reloads from MongoDB. */
    open fun invalidateCache() {
        zeroCycleCache = null
    }

    private fun loadZeroCycle(): List<DelegationV2> {
        val cached = zeroCycleCache
        if (cached != null) return cached.values.toList()
        val loaded =
            repository
                .findByTransitionAtBlockIsNullAndStatusIn(listOf(DelegationStatusV2.QUEUED))
                .associateByTo(mutableMapOf()) { it.id }
        zeroCycleCache = loaded
        return loaded.values.toList()
    }

    private fun updateZeroCycleCache(updates: List<DelegationV2>) {
        val cache = zeroCycleCache ?: return
        updates.forEach { u ->
            val isZeroCycle = u.status == DelegationStatusV2.QUEUED && u.transitionAtBlock == null
            if (isZeroCycle) cache[u.id] = u else cache.remove(u.id)
        }
    }

    // ------------------------------ scheduled transitions ------------------------------

    private fun applyScheduledTransitions(block: Block, working: MutableMap<String, DelegationV2>) {
        working.values.toList().forEach { d ->
            if (d.transitionAtBlock != block.number) return@forEach
            val next =
                when (d.status) {
                    DelegationStatusV2.QUEUED ->
                        d.copy(status = DelegationStatusV2.ACTIVE, transitionAtBlock = null)
                    DelegationStatusV2.EXITING ->
                        d.copy(status = DelegationStatusV2.EXITED, transitionAtBlock = null)
                    else -> return@forEach
                }
            working[d.id] = next
        }
    }

    /**
     * Zero-cycle delegations are those waiting on a validator that hadn't yet been activated when
     * the delegation was created. Once the validator gains a non-zero `startBlock`, re-derive
     * [DelegationV2.transitionAtBlock] so the next scheduled-transition pass can flip the status.
     */
    private fun refreshZeroCycle(
        block: Block,
        working: MutableMap<String, DelegationV2>,
        validators: Map<String, ValidatorV2>,
    ) {
        working.values.toList().forEach { d ->
            if (d.status != DelegationStatusV2.QUEUED) return@forEach
            if (d.transitionAtBlock != null) return@forEach
            val next = nextCycleStart(d.validator, block.number, validators) ?: return@forEach
            if (next > block.number) {
                working[d.id] = d.copy(transitionAtBlock = next)
            }
        }
    }

    // ------------------------------ validator preload ------------------------------

    /**
     * One batched read of every [ValidatorV2] this block might need to inspect: the validator of
     * every delegation we've loaded into `working`, plus any validator referenced by a
     * `DelegationInitiated` event (which can name a validator we don't yet have a delegation for).
     *
     * Replaces per-call `findByIdOrNull` lookups in [nextCycleStart] and [validatorExitBlock] —
     * relevant because [refreshZeroCycle] previously hit the repository once per cached zero-cycle
     * delegation per block.
     */
    private fun preloadValidators(
        inWorking: Collection<DelegationV2>,
        events: List<IndexedEvent>,
    ): Map<String, ValidatorV2> {
        val ids: Set<String> = buildSet {
            inWorking.forEach { add(it.validator) }
            events.forEach { ev ->
                if (ev.eventType == "DelegationInitiated") {
                    ev.params.getAsString("validator")?.lowercase()?.let { add(it) }
                }
            }
        }
        if (ids.isEmpty()) return emptyMap()
        return validatorRepository.findAllById(ids).associateBy { it.id }
    }

    // ------------------------------ event mutations ------------------------------

    private fun applyEventMutation(
        ev: IndexedEvent,
        block: Block,
        working: MutableMap<String, DelegationV2>,
        tokenIdToId: MutableMap<String, String>,
        validatorToIds: MutableMap<String, MutableList<String>>,
        validators: Map<String, ValidatorV2>,
    ) {
        if (!isRelevantEvent(ev)) return
        when (ev.eventType) {
            "DelegationInitiated" ->
                onDelegationInitiated(ev, block, working, tokenIdToId, validatorToIds, validators)
            "DelegationExitRequested" -> onDelegationExitRequested(ev, block, working, validators)
            "DelegationWithdrawn" -> onDelegationWithdrawn(ev, working)
            "DelegationRewardsClaimed" -> onDelegationRewardsClaimed(ev, working)
            "Transfer" -> onTransfer(ev, working, tokenIdToId)
            "ValidationSignaledExit" ->
                onValidationSignaledExit(ev, block, working, validatorToIds, validators)
            "ValidationWithdrawn" -> onValidationWithdrawn(ev, working, validatorToIds)
            else -> {
                /* ignored */
            }
        }
    }

    private fun isRelevantEvent(ev: IndexedEvent): Boolean =
        when (ev.eventType) {
            // Only the builtin staker can signal validator-level lifecycle.
            "ValidationSignaledExit",
            "ValidationWithdrawn" -> ev.address.equals(stakerSC, ignoreCase = true)
            // Delegation lifecycle is emitted by the Stargate contracts, not the staker.
            "DelegationInitiated",
            "DelegationExitRequested",
            "DelegationWithdrawn",
            "DelegationRewardsClaimed",
            "Transfer" -> !ev.address.equals(stakerSC, ignoreCase = true)
            else -> false
        }

    private fun onDelegationInitiated(
        ev: IndexedEvent,
        block: Block,
        working: MutableMap<String, DelegationV2>,
        tokenIdToId: MutableMap<String, String>,
        validatorToIds: MutableMap<String, MutableList<String>>,
        validators: Map<String, ValidatorV2>,
    ) {
        val delegationId = ev.params.getAsString("delegationId") ?: return
        val tokenId = ev.params.getAsString("tokenId") ?: return
        val validator = ev.params.getAsString("validator")?.lowercase() ?: return
        val levelOrdinal = ev.params.getAsString("levelId")?.toIntOrNull() ?: return
        val level = TokenLevel.fromOrdinal(levelOrdinal) ?: return
        val amount = ev.params.getAsString("amount") ?: "0"
        val owner = ev.origin ?: return

        val transitionAt = nextCycleStart(validator, block.number, validators)

        working[delegationId] =
            DelegationV2(
                id = delegationId,
                validator = validator,
                tokenId = tokenId,
                owner = owner,
                status = DelegationStatusV2.QUEUED,
                tokenLevel = level,
                stakedAmount = amount,
                totalRewardsClaimed = BigInteger.ZERO,
                txId = ev.txId,
                transitionAtBlock = transitionAt,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )
        tokenIdToId[tokenId] = delegationId
        validatorToIds.getOrPut(validator) { mutableListOf() }.add(delegationId)
    }

    private fun onDelegationExitRequested(
        ev: IndexedEvent,
        block: Block,
        working: MutableMap<String, DelegationV2>,
        validators: Map<String, ValidatorV2>,
    ) {
        val delegationId = ev.params.getAsString("delegationId") ?: return
        val current = working[delegationId] ?: return
        if (current.status == DelegationStatusV2.EXITED) return

        working[delegationId] =
            current.copy(
                status = DelegationStatusV2.EXITING,
                transitionAtBlock = nextCycleStart(current.validator, block.number, validators),
                txId = ev.txId,
            )
    }

    private fun onDelegationWithdrawn(ev: IndexedEvent, working: MutableMap<String, DelegationV2>) {
        val delegationId = ev.params.getAsString("delegationId") ?: return
        val current = working[delegationId] ?: return
        working[delegationId] =
            current.copy(
                status = DelegationStatusV2.EXITED,
                transitionAtBlock = null,
                txId = ev.txId,
            )
    }

    private fun onDelegationRewardsClaimed(
        ev: IndexedEvent,
        working: MutableMap<String, DelegationV2>,
    ) {
        val delegationId = ev.params.getAsString("delegationId") ?: return
        val amount = ev.params.getAsBigInteger("amount") ?: return
        val current = working[delegationId] ?: return
        working[delegationId] =
            current.copy(totalRewardsClaimed = current.totalRewardsClaimed + amount)
    }

    private fun onTransfer(
        ev: IndexedEvent,
        working: MutableMap<String, DelegationV2>,
        tokenIdToId: Map<String, String>,
    ) {
        val tokenId = ev.params.getAsString("tokenId") ?: return
        val to = ev.params.getAsString("to") ?: return
        val delegationId = tokenIdToId[tokenId] ?: return
        val current = working[delegationId] ?: return
        if (current.status == DelegationStatusV2.EXITED) return
        working[delegationId] = current.copy(owner = to)
    }

    /**
     * The validator just signalled exit. Move all its still-active delegations to EXITING. We
     * estimate the transition block using the validator's next cycle boundary: if `ValidatorV2`
     * already carries an `exitBlock` from a prior walk, prefer that; otherwise the next-cycle
     * estimate will be reconciled at the next epoch walk.
     */
    private fun onValidationSignaledExit(
        ev: IndexedEvent,
        block: Block,
        working: MutableMap<String, DelegationV2>,
        validatorToIds: Map<String, List<String>>,
        validators: Map<String, ValidatorV2>,
    ) {
        val validatorId = ev.params.getAsString("validator")?.lowercase() ?: return
        val ids = validatorToIds[validatorId] ?: return
        val exitAt =
            validatorExitBlock(validatorId, validators)
                ?: nextCycleStart(validatorId, block.number, validators)

        ids.forEach { id ->
            val current = working[id] ?: return@forEach
            if (
                current.status == DelegationStatusV2.EXITED ||
                    current.status == DelegationStatusV2.EXITING
            ) {
                return@forEach
            }
            working[id] =
                current.copy(
                    status = DelegationStatusV2.EXITING,
                    transitionAtBlock = exitAt,
                    txId = ev.txId,
                )
        }
    }

    /**
     * Validator fully withdrew — chain has dropped them from the staker. Force every remaining
     * delegation to EXITED. This is the V2 equivalent of V1's "validator disappeared" detection,
     * but driven by an explicit chain event rather than periodic set diffing.
     */
    private fun onValidationWithdrawn(
        ev: IndexedEvent,
        working: MutableMap<String, DelegationV2>,
        validatorToIds: Map<String, List<String>>,
    ) {
        val validatorId = ev.params.getAsString("validator")?.lowercase() ?: return
        val ids = validatorToIds[validatorId] ?: return
        ids.forEach { id ->
            val current = working[id] ?: return@forEach
            if (current.status == DelegationStatusV2.EXITED) return@forEach
            working[id] =
                current.copy(
                    status = DelegationStatusV2.EXITED,
                    transitionAtBlock = null,
                    txId = ev.txId,
                )
        }
    }

    // ------------------------------ validator helpers (MongoDB-backed)
    // ------------------------------

    /**
     * Next cycle-boundary block for [validatorId] after [blockNumber], computed from the persisted
     * `ValidatorV2` row in the supplied [validators] map (preloaded once per block by
     * [preloadValidators]). Returns `null` if the validator isn't known or hasn't been activated
     * (no `startBlock`/`cyclePeriodLength`).
     */
    private fun nextCycleStart(
        validatorId: String,
        blockNumber: Long,
        validators: Map<String, ValidatorV2>,
    ): Long? {
        val v = validators[validatorId] ?: return null
        val start = v.startBlock ?: return null
        val period = v.cyclePeriodLength ?: return null
        if (start <= 0L || period <= 0L) return null
        if (blockNumber < start) return start
        val offset = blockNumber - start
        val positionInCycle = offset % period
        val currentCycleStart = blockNumber - positionInCycle
        return currentCycleStart + period
    }

    private fun validatorExitBlock(
        validatorId: String,
        validators: Map<String, ValidatorV2>,
    ): Long? = validators[validatorId]?.exitBlock
}
