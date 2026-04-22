package org.vechain.indexer.b3tr.challenges

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.EventCriteria
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.EventLogsRequest
import org.vechain.indexer.thor.model.LogsOptions
import org.vechain.indexer.thor.model.LogsRange
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trChallengesService(
    private val repository: B3trChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val thorClient: ThorClient,
    @Value("\${business-event.substitutions.EMISSIONS}") emissionsContractAddress: String,
) {
    private var runtimeState: ChallengeRuntimeState? = null

    private val normalizedEmissionsContractAddress = HexUtils.normalise(emissionsContractAddress)
    private val challengeQueryBatchSize = 500

    private val emissionDistributedSignature =
        HexUtils.addPrefix(
            ContractUtils.getEventSignature("EmissionDistributed(uint256,uint256,uint256,uint256)")
        )
    private val emissionDistributedV2Signature =
        HexUtils.addPrefix(
            ContractUtils.getEventSignature(
                "EmissionDistributedV2(uint256,uint256,uint256,uint256,uint256)"
            )
        )

    private val trackedEventTypes =
        setOf(
            "ChallengeCreated",
            "SplitWinConfigured",
            "ChallengeInviteAdded",
            "ChallengeJoined",
            "ChallengeLeft",
            "ChallengeDeclined",
            "ChallengeCancelled",
            "ChallengeActivated",
            "ChallengeInvalidated",
            "ChallengeCompleted",
            "ChallengePayoutClaimed",
            "SplitWinPrizeClaimed",
            "SplitWinCreatorRefunded",
            "ChallengeRefundClaimed",
            "EmissionDistributed",
            "EmissionDistributedV2",
        )

    open fun findByChallengeId(challengeId: Long): B3trChallenge? =
        repository.findByIdOrNull(B3trChallenge.documentId(challengeId))

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<B3trChallenge>, List<B3trChallenge>> {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return emptyList<B3trChallenge>() to emptyList()

        var currentRuntimeState = getRuntimeState()

        val allRecordIds =
            relevantEvents
                .filter(::isChallengeEvent)
                .map { B3trChallenge.documentId(getChallengeId(it)) }
                .toSet()

        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<B3trChallenge>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(relevantEvents).forEach { (_, blockEvents) ->
            accumulator.startBlock()

            val previousRuntimeState = currentRuntimeState
            currentRuntimeState = updateRuntimeState(currentRuntimeState, blockEvents)

            blockEvents
                .filter(::isChallengeEvent)
                .groupBy { getChallengeId(it) }
                .forEach { (challengeId, eventsForChallenge) ->
                    val recordId = B3trChallenge.documentId(challengeId)
                    val (existing, nextVersion) = accumulator.resolve(recordId)
                    val updated =
                        buildChallengeDocument(
                            challengeId = challengeId,
                            existing = existing,
                            eventsForChallenge = eventsForChallenge,
                            version = nextVersion,
                            runtimeState = currentRuntimeState,
                        )

                    if (existing != updated) {
                        accumulator.put(recordId, existing, updated)
                    }
                }

            if (currentRuntimeState.currentRound != previousRuntimeState.currentRound) {
                refreshAffectedChallenges(
                    accumulator = accumulator,
                    previousRound = previousRuntimeState.currentRound,
                    currentRound = currentRuntimeState.currentRound,
                    runtimeState = currentRuntimeState,
                )
            }
        }

        runtimeState = currentRuntimeState

        return accumulator.results()
    }

    open fun invalidateRuntimeState() {
        runtimeState = null
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<B3trChallenge>, existing: List<B3trChallenge>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }

    private fun buildChallengeDocument(
        challengeId: Long,
        existing: B3trChallenge?,
        eventsForChallenge: List<IndexedEvent>,
        version: Int,
        runtimeState: ChallengeRuntimeState,
    ): B3trChallenge {
        val latestEvent = eventsForChallenge.last()
        val createdEvent = eventsForChallenge.firstOrNull { it.eventType == "ChallengeCreated" }

        require(existing == null || createdEvent == null) {
            "Unexpected ChallengeCreated event for existing challenge $challengeId"
        }

        val state =
            existing?.toMutableState()
                ?: B3trChallengeEventUtils.createChallengeState(
                    createdEvent ?: error("Missing ChallengeCreated for $challengeId")
                )

        eventsForChallenge
            .filterNot { it.eventType == "ChallengeCreated" }
            .forEach { event -> B3trChallengeEventUtils.applyEvent(challengeId, state, event) }

        return state.toDocument(challengeId, version, latestEvent, runtimeState)
    }

    private fun getChallengeId(event: IndexedEvent): Long =
        when (val value = event.params.getReturnValues()["challengeId"]) {
            is Number -> value.toLong()
            else -> value?.toString()?.toLong() ?: error("Expected challengeId value")
        }

    private fun isChallengeEvent(event: IndexedEvent): Boolean =
        "challengeId" in event.params.getReturnValues()

    private fun updateRuntimeState(
        state: ChallengeRuntimeState,
        events: List<IndexedEvent>,
    ): ChallengeRuntimeState {
        val latestRoundEvent =
            events.lastOrNull {
                it.eventType == "EmissionDistributed" || it.eventType == "EmissionDistributedV2"
            }

        return ChallengeRuntimeState(
            currentRound = latestRoundEvent?.let(ActionSummaryUtils::getCycle) ?: state.currentRound
        )
    }

    private suspend fun getRuntimeState(): ChallengeRuntimeState {
        runtimeState?.let {
            return it
        }

        runtimeState = ChallengeRuntimeState(currentRound = restoreCurrentRound())
        return runtimeState!!
    }

    private suspend fun restoreCurrentRound(): Int {
        val bestBlock = thorClient.getBlock(BlockRevision.Keyword.BEST)
        val latestEmission =
            thorClient.getEventLogs(
                EventLogsRequest(
                    range = LogsRange(unit = "block", from = 0, to = bestBlock.number),
                    options = LogsOptions(offset = 0, limit = 1),
                    criteriaSet =
                        listOf(
                            EventCriteria(
                                address = normalizedEmissionsContractAddress,
                                topic0 = emissionDistributedSignature,
                            ),
                            EventCriteria(
                                address = normalizedEmissionsContractAddress,
                                topic0 = emissionDistributedV2Signature,
                            ),
                        ),
                    order = "desc",
                )
            )

        return latestEmission.firstOrNull()?.let(::decodeEmissionCycle) ?: 0
    }

    private fun decodeEmissionCycle(eventLog: EventLog): Int {
        val cycleTopic = eventLog.topics.getOrNull(1) ?: error("Missing cycle topic in emission")
        return HexUtils.toBigInteger(cycleTopic).toInt()
    }

    private fun refreshAffectedChallenges(
        accumulator: VersionedDocumentAccumulator<B3trChallenge>,
        previousRound: Int,
        currentRound: Int,
        runtimeState: ChallengeRuntimeState,
    ) {
        val affectedCriteria = buildAffectedChallengesCriteria(previousRound, currentRound)
        var lastChallengeId: Long? = null

        while (true) {
            val criteria = buildList {
                add(affectedCriteria)
                lastChallengeId?.let { add(Criteria.where(B3trChallenge::challengeId.name).gt(it)) }
            }

            val query =
                Query(Criteria().andOperator(*criteria.toTypedArray()))
                    .with(Sort.by(Sort.Direction.ASC, B3trChallenge::challengeId.name))
                    .limit(challengeQueryBatchSize)

            val batch = mongoTemplate.find(query, B3trChallenge::class.java)
            if (batch.isEmpty()) {
                return
            }

            batch.forEach { record ->
                val (existing, nextVersion) = accumulator.resolve(record.getDocumentId())
                val current = existing ?: record
                val refreshed = current.withRuntimeState(runtimeState)

                if (current != refreshed) {
                    accumulator.put(
                        recordId = current.getDocumentId(),
                        existing = current,
                        updatedRecord = refreshed.copy(version = nextVersion),
                    )
                }
            }

            lastChallengeId = batch.last().challengeId
        }
    }

    private fun buildAffectedChallengesCriteria(previousRound: Int, currentRound: Int): Criteria {
        val lowerBound = minOf(previousRound, currentRound)
        val upperBound = maxOf(previousRound, currentRound)

        return Criteria()
            .orOperator(
                Criteria.where(B3trChallenge::startRound.name).gt(lowerBound).lte(upperBound),
                Criteria.where(B3trChallenge::endRound.name).gte(lowerBound).lt(upperBound),
            )
    }
}
