package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trChallengesService(
    private val repository: B3trChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private var runtimeState: ChallengeRuntimeState? = null

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
            "MaxParticipantsUpdated",
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

            if (hasGlobalRuntimeChange(blockEvents)) {
                refreshAllChallenges(accumulator, currentRuntimeState)
            }
        }

        runtimeState = currentRuntimeState

        return accumulator.results()
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

    private fun hasGlobalRuntimeChange(events: List<IndexedEvent>): Boolean =
        events.any {
            it.eventType == "MaxParticipantsUpdated" ||
                it.eventType == "EmissionDistributed" ||
                it.eventType == "EmissionDistributedV2"
        }

    private fun updateRuntimeState(
        state: ChallengeRuntimeState,
        events: List<IndexedEvent>,
    ): ChallengeRuntimeState {
        val latestRoundEvent =
            events.lastOrNull {
                it.eventType == "EmissionDistributed" || it.eventType == "EmissionDistributedV2"
            }
        val latestMaxParticipantsEvent =
            events.lastOrNull { it.eventType == "MaxParticipantsUpdated" }
        return ChallengeRuntimeState(
            currentRound =
                latestRoundEvent?.let(ActionSummaryUtils::getCycle) ?: state.currentRound,
            maxParticipants =
                latestMaxParticipantsEvent?.params?.getAsString("newValue")?.toInt()
                    ?: state.maxParticipants,
        )
    }

    private fun getRuntimeState(): ChallengeRuntimeState {
        if (runtimeState != null) {
            return runtimeState!!
        }

        val latestRecord = repository.getLatestRecord()
        runtimeState =
            latestRecord?.let { ChallengeRuntimeState(it.currentRound, it.maxParticipants) }
                ?: ChallengeRuntimeState()
        return runtimeState!!
    }

    private fun refreshAllChallenges(
        accumulator: VersionedDocumentAccumulator<B3trChallenge>,
        runtimeState: ChallengeRuntimeState,
    ) {
        var page = PageRequest.of(0, 500) as org.springframework.data.domain.Pageable
        while (true) {
            val slice = repository.findAll(page)
            slice.content.forEach { record ->
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

            if (!slice.hasNext()) {
                return
            }
            page = slice.nextPageable()
        }
    }
}
