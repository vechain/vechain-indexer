package org.vechain.indexer.history

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.replaceRoot
import org.springframework.data.mongodb.core.aggregation.Aggregation.sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorSnapshot

@Profile("history")
@Service
class DelegationLifecycleHistoryService(
    private val mongoTemplate: MongoTemplate,
    private val validatorDelegationService: ValidatorDelegationService,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerSC: String,
    @param:Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
    private val stargateNftContract: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val statesById = linkedMapOf<String, DelegationLifecycleState>()
    private val tokenIdToId = linkedMapOf<String, String>()
    private val validatorToIds = linkedMapOf<String, MutableSet<String>>()
    @Volatile private var isLoaded = false

    suspend fun onBlockStart(
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): List<IndexedHistoryEvent> {
        ensureLoaded()
        resolveUnknownStartBlocks(block, validatorSnapshots)

        val scheduled =
            statesById.values
                .filter {
                    (it.status == Status.QUEUED || it.status == Status.EXITING) &&
                        it.nextCycle == block.number
                }
                .sortedBy { it.delegationId }

        val historyEvents = mutableListOf<IndexedHistoryEvent>()
        scheduled.forEachIndexed { index, state ->
            val nextStatus = validatorDelegationService.nextStatus(state.status)
            val updated = state.copy(status = nextStatus)
            putState(updated)
            historyEvents.add(
                createSyntheticHistoryEvent(
                    block = block,
                    state = updated,
                    eventName =
                        EventUtils.determineDelegationEventType(updated.status, updated.forceExit)
                            ?: return@forEachIndexed,
                    order = BLOCK_START_ORDER_BASE + index,
                )
            )
            if (updated.status == Status.EXITED) {
                removeState(updated.delegationId)
            }
        }

        return historyEvents
    }

    suspend fun onEvent(
        event: IndexedEvent,
        historyEvent: IndexedHistoryEvent?,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        order: Int,
    ): EventResult {
        return when (event.eventType) {
            HistoryEventName.STARGATE_DELEGATE_REQUEST.name ->
                handleDelegateRequest(event, historyEvent, block, validatorSnapshots, order)
            HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST.name ->
                handleDelegateExitRequest(event, historyEvent, block, order)
            HistoryEventName.STARGATE_DELEGATE_REQUEST_CANCELLED.name ->
                handleDelegateRequestCancelled(event, historyEvent, order)
            "Transfer" -> handleTransfer(event, historyEvent, order)
            "ValidatorExitRequested",
            "ValidationSignaledExit" ->
                handleValidatorExitRequested(event, block, validatorSnapshots, historyEvent)
            else -> EventResult(historyEvent)
        }
    }

    fun onBlockEnd(
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): List<IndexedHistoryEvent> {
        ensureLoaded()
        if (validatorSnapshots.isEmpty()) return emptyList()

        val currentValidators = validatorSnapshots.keys
        val disappeared = validatorToIds.keys.filter { it !in currentValidators }.sorted()
        if (disappeared.isEmpty()) return emptyList()

        val historyEvents = mutableListOf<IndexedHistoryEvent>()
        var order = BLOCK_END_ORDER_BASE

        disappeared.forEach { validatorId ->
            val delegationIds = validatorToIds[validatorId]?.toList()?.sorted().orEmpty()
            delegationIds.forEach { delegationId ->
                val state = statesById[delegationId] ?: return@forEach
                val exited = state.copy(status = Status.EXITED, forceExit = true, nextCycle = null)
                historyEvents.add(
                    createSyntheticHistoryEvent(
                        block = block,
                        state = exited,
                        eventName = HistoryEventName.STARGATE_DELEGATION_EXITED_VALIDATOR,
                        order = order++,
                    )
                )
                removeState(delegationId)
            }
        }

        return historyEvents
    }

    fun invalidate() {
        statesById.clear()
        tokenIdToId.clear()
        validatorToIds.clear()
        isLoaded = false
    }

    private suspend fun handleDelegateRequest(
        event: IndexedEvent,
        historyEvent: IndexedHistoryEvent?,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        order: Int,
    ): EventResult {
        val delegationId =
            event.params.getAsString("delegationId") ?: return EventResult(historyEvent)
        val tokenId = event.params.getAsString("tokenId") ?: return EventResult(historyEvent)
        val validator = event.params.getAsString("validator") ?: return EventResult(historyEvent)
        val owner =
            event.params.getAsString("owner") ?: event.origin ?: return EventResult(historyEvent)
        val (cycleLength, nextCycle) =
            validatorDelegationService.resolveCycleInfo(validator, block.number, validatorSnapshots)

        val state =
            DelegationLifecycleState(
                delegationId = delegationId,
                tokenId = tokenId,
                validator = validator,
                owner = owner,
                status = Status.QUEUED,
                nextCycle = nextCycle,
                cycleLength = cycleLength,
                forceExit = false,
                txId = event.txId,
            )
        putState(state)
        return EventResult(historyEvent.withLifecycleState(state, order))
    }

    private suspend fun handleDelegateExitRequest(
        event: IndexedEvent,
        historyEvent: IndexedHistoryEvent?,
        block: Block,
        order: Int,
    ): EventResult {
        val delegationId =
            event.params.getAsString("delegationId") ?: return EventResult(historyEvent)
        val existing = statesById[delegationId]
        val validator =
            existing?.validator
                ?: event.params.getAsString("validator")
                ?: return EventResult(historyEvent)
        val tokenId =
            existing?.tokenId
                ?: event.params.getAsString("tokenId")
                ?: return EventResult(historyEvent)
        val owner =
            existing?.owner
                ?: event.params.getAsString("owner")
                ?: event.origin
                ?: return EventResult(historyEvent)
        val cycleLength = existing?.cycleLength ?: 0L
        val nextCycle =
            validatorDelegationService.resolveNextCycleBlock(
                existing?.nextCycle,
                if (cycleLength > 0L) cycleLength else 1L,
                block.number,
            )

        val state =
            DelegationLifecycleState(
                delegationId = delegationId,
                tokenId = tokenId,
                validator = validator,
                owner = owner,
                status = Status.EXITING,
                nextCycle = nextCycle,
                cycleLength = cycleLength,
                forceExit = existing?.forceExit ?: false,
                txId = event.txId,
            )
        putState(state)
        return EventResult(historyEvent.withLifecycleState(state, order))
    }

    private fun handleDelegateRequestCancelled(
        event: IndexedEvent,
        historyEvent: IndexedHistoryEvent?,
        order: Int,
    ): EventResult {
        val delegationId =
            event.params.getAsString("delegationId") ?: return EventResult(historyEvent)
        val existing = statesById[delegationId]
        removeState(delegationId)

        val state =
            DelegationLifecycleState(
                delegationId = delegationId,
                tokenId =
                    existing?.tokenId
                        ?: event.params.getAsString("tokenId")
                        ?: return EventResult(historyEvent),
                validator =
                    existing?.validator
                        ?: event.params.getAsString("validator")
                        ?: return EventResult(historyEvent),
                owner =
                    existing?.owner
                        ?: event.params.getAsString("owner")
                        ?: event.origin
                        ?: return EventResult(historyEvent),
                status = Status.EXITED,
                nextCycle = null,
                cycleLength = existing?.cycleLength ?: 0L,
                forceExit = false,
                txId = event.txId,
            )

        return EventResult(historyEvent.withLifecycleState(state, order))
    }

    private fun handleTransfer(
        event: IndexedEvent,
        historyEvent: IndexedHistoryEvent?,
        order: Int,
    ): EventResult {
        if (!event.address.equals(stargateNftContract, ignoreCase = true))
            return EventResult(historyEvent)

        val tokenId = event.params.getAsString("tokenId") ?: return EventResult(historyEvent)
        val delegationId = tokenIdToId[tokenId] ?: return EventResult(historyEvent)
        val existing = statesById[delegationId] ?: return EventResult(historyEvent)
        if (existing.status == Status.EXITED) return EventResult(historyEvent)

        val to = event.params.getAsString("to") ?: return EventResult(historyEvent)
        val updated = existing.copy(owner = to, txId = event.txId)
        putState(updated)

        return EventResult(historyEvent.withLifecycleState(updated, order))
    }

    private suspend fun handleValidatorExitRequested(
        event: IndexedEvent,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
        historyEvent: IndexedHistoryEvent?,
    ): EventResult {
        if (!event.address.equals(stakerSC, ignoreCase = true)) return EventResult(historyEvent)

        val validatorId = event.params.getAsString("validator") ?: return EventResult(historyEvent)
        val exitAt =
            validatorDelegationService.getValidatorExitBlock(validatorId, validatorSnapshots)
        val delegationIds = validatorToIds[validatorId]?.toList()?.sorted().orEmpty()

        delegationIds.forEach { delegationId ->
            val existing = statesById[delegationId] ?: return@forEach
            if (existing.status == Status.EXITED || existing.status == Status.EXITING)
                return@forEach
            putState(
                existing.copy(
                    status = Status.EXITING,
                    nextCycle = exitAt,
                    forceExit = true,
                    txId = event.txId,
                )
            )
        }

        return EventResult(historyEvent)
    }

    private suspend fun resolveUnknownStartBlocks(
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ) {
        val unknown =
            statesById.values
                .filter {
                    it.status == Status.QUEUED && (it.nextCycle == null || it.nextCycle == 0L)
                }
                .groupBy { it.validator }

        if (unknown.isEmpty()) return

        unknown.keys.sorted().forEach { validatorId ->
            val snapshot = validatorSnapshots[validatorId]
            if (snapshot != null && snapshot.startBlock == 0L) return@forEach

            val (cycleLength, nextCycle) =
                validatorDelegationService.resolveCycleInfo(
                    validatorId,
                    block.number,
                    validatorSnapshots,
                )
            if (nextCycle == 0L) return@forEach

            unknown[validatorId].orEmpty().forEach { state ->
                putState(state.copy(cycleLength = cycleLength, nextCycle = nextCycle))
            }
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return

        val aggregation =
            Aggregation.newAggregation(
                match(
                    Criteria.where(IndexedHistoryEvent.DELEGATION_LIFECYCLE_STATUS_FIELD)
                        .exists(true)
                        .ne(null)
                ),
                sort(
                    Sort.by(
                        Sort.Order.asc(IndexedHistoryEvent::delegationId.name),
                        Sort.Order.desc(IndexedHistoryEvent::blockNumber.name),
                        Sort.Order.desc(IndexedHistoryEvent.DELEGATION_LIFECYCLE_ORDER_FIELD),
                    )
                ),
                group(IndexedHistoryEvent::delegationId.name)
                    .first(Aggregation.ROOT)
                    .`as`("latest"),
                replaceRoot("latest"),
            )

        val latestRows =
            mongoTemplate
                .aggregate(
                    aggregation,
                    mongoTemplate.getCollectionName(IndexedHistoryEvent::class.java),
                    IndexedHistoryEvent::class.java,
                )
                .mappedResults

        latestRows.forEach { row ->
            val delegationId = row.delegationId ?: return@forEach
            val lifecycleStatus = row.delegationLifecycleStatus ?: return@forEach
            if (lifecycleStatus == Status.EXITED) return@forEach

            val owner =
                when (row.eventName) {
                    HistoryEventName.TRANSFER_NFT -> row.to
                    else -> row.owner ?: row.origin
                } ?: return@forEach

            putState(
                DelegationLifecycleState(
                    delegationId = delegationId,
                    tokenId = row.tokenId ?: return@forEach,
                    validator = row.validator ?: return@forEach,
                    owner = owner,
                    status = lifecycleStatus,
                    nextCycle = row.delegationLifecycleNextCycle,
                    cycleLength = row.delegationLifecycleCycleLength ?: 0L,
                    forceExit = row.delegationLifecycleForceExit == true,
                    txId = row.txId,
                )
            )
        }

        logger.info("Loaded {} delegation lifecycle states from history", statesById.size)
        isLoaded = true
    }

    private fun putState(state: DelegationLifecycleState) {
        statesById[state.delegationId]?.let { previous ->
            if (previous.validator != state.validator) {
                validatorToIds[previous.validator]?.remove(previous.delegationId)
                if (validatorToIds[previous.validator].isNullOrEmpty()) {
                    validatorToIds.remove(previous.validator)
                }
            }
            if (previous.tokenId != state.tokenId) {
                tokenIdToId.remove(previous.tokenId)
            }
        }

        statesById[state.delegationId] = state
        tokenIdToId[state.tokenId] = state.delegationId
        validatorToIds.getOrPut(state.validator) { linkedSetOf() }.add(state.delegationId)
    }

    private fun removeState(delegationId: String) {
        val removed = statesById.remove(delegationId) ?: return
        tokenIdToId.remove(removed.tokenId)
        validatorToIds[removed.validator]?.remove(delegationId)
        if (validatorToIds[removed.validator].isNullOrEmpty()) {
            validatorToIds.remove(removed.validator)
        }
    }

    private fun createSyntheticHistoryEvent(
        block: Block,
        state: DelegationLifecycleState,
        eventName: HistoryEventName,
        order: Int,
    ): IndexedHistoryEvent =
        IndexedHistoryEvent(
            id = IdUtils.generateId(state.delegationId, eventName.name, block.number.toString()),
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            blockId = block.id,
            txId = state.txId,
            eventName = eventName,
            tokenId = state.tokenId,
            delegationId = state.delegationId,
            validator = state.validator,
            owner = state.owner,
            origin = state.owner,
            delegationLifecycleStatus = state.status,
            delegationLifecycleNextCycle = state.nextCycle,
            delegationLifecycleCycleLength = state.cycleLength,
            delegationLifecycleForceExit = state.forceExit,
            delegationLifecycleOrder = order,
        )

    private fun IndexedHistoryEvent?.withLifecycleState(
        state: DelegationLifecycleState,
        order: Int,
    ): IndexedHistoryEvent? =
        this?.copy(
            delegationLifecycleStatus = state.status,
            delegationLifecycleNextCycle = state.nextCycle,
            delegationLifecycleCycleLength = state.cycleLength,
            delegationLifecycleForceExit = state.forceExit,
            delegationLifecycleOrder = order,
        )

    private data class DelegationLifecycleState(
        val delegationId: String,
        val tokenId: String,
        val validator: String,
        val owner: String,
        val status: Status,
        val nextCycle: Long?,
        val cycleLength: Long,
        val forceExit: Boolean,
        val txId: String,
    )

    data class EventResult(
        val historyEvent: IndexedHistoryEvent?,
        val additionalEvents: List<IndexedHistoryEvent> = emptyList(),
    )

    companion object {
        private const val BLOCK_START_ORDER_BASE = 100
        private const val BLOCK_END_ORDER_BASE = 900000
    }
}
