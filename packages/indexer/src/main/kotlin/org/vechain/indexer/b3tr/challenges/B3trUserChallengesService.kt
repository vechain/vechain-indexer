package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trUserChallengesService(
    private val repository: B3trUserChallengeRepository,
    private val challengeRepository: B3trChallengeRepository,
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
            "B3TR_ActionReward",
        )

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<B3trUserChallenge>, List<B3trUserChallenge>> {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return emptyList<B3trUserChallenge>() to emptyList()

        var currentRuntimeState = getRuntimeState()
        val impactedChallengeIds =
            relevantEvents.filter(::isChallengeEvent).map(::getChallengeId).toSet()
        val rewardWallets =
            relevantEvents
                .filter { it.eventType == "B3TR_ActionReward" }
                .mapNotNull { it.params.getAsString("receiver") }
                .map(HexUtils::normalise)
                .toSet()
        val currentDocs =
            mutableMapOf<String, B3trUserChallenge>().apply {
                impactedChallengeIds.forEach { challengeId ->
                    repository.findAllByChallengeId(challengeId).forEach {
                        put(it.getDocumentId(), it)
                    }
                }
                rewardWallets.forEach { wallet ->
                    repository.findAllByWallet(wallet).forEach { put(it.getDocumentId(), it) }
                }
            }
        val currentChallenges = mutableMapOf<Long, B3trChallenge>()
        hydrateChallenges(impactedChallengeIds, currentChallenges)
        hydrateChallenges(
            currentDocs.values.map(B3trUserChallenge::challengeId).toSet(),
            currentChallenges,
        )

        val accumulator =
            VersionedDocumentAccumulator<B3trUserChallenge>(
                findById = { id -> currentDocs[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(relevantEvents).forEach { (_, blockEvents) ->
            accumulator.startBlock()
            currentRuntimeState = updateRuntimeState(currentRuntimeState, blockEvents)

            blockEvents.filter(::isChallengeEvent).groupBy(::getChallengeId).forEach {
                (challengeId, eventsForChallenge) ->
                val challenge =
                    buildChallengeSnapshot(
                        challengeId = challengeId,
                        existing = currentChallenges[challengeId],
                        eventsForChallenge = eventsForChallenge,
                        runtimeState = currentRuntimeState,
                    )
                currentChallenges[challengeId] = challenge
                rebuildChallengeWalletDocs(
                    challenge = challenge,
                    latestEvent = eventsForChallenge.last(),
                    currentDocs = currentDocs,
                    accumulator = accumulator,
                )
            }

            blockEvents
                .filter { it.eventType == "B3TR_ActionReward" }
                .forEach { rewardEvent ->
                    applyActionReward(
                        rewardEvent = rewardEvent,
                        runtimeState = currentRuntimeState,
                        currentDocs = currentDocs,
                        currentChallenges = currentChallenges,
                        accumulator = accumulator,
                    )
                }

            if (hasGlobalRuntimeChange(blockEvents)) {
                refreshAllUserChallenges(
                    latestEvent = blockEvents.last(),
                    runtimeState = currentRuntimeState,
                    currentDocs = currentDocs,
                    currentChallenges = currentChallenges,
                    accumulator = accumulator,
                )
            }
        }

        runtimeState = currentRuntimeState
        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<B3trUserChallenge>, existing: List<B3trUserChallenge>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }

    private fun rebuildChallengeWalletDocs(
        challenge: B3trChallenge,
        latestEvent: IndexedEvent,
        currentDocs: MutableMap<String, B3trUserChallenge>,
        accumulator: VersionedDocumentAccumulator<B3trUserChallenge>,
    ) {
        val docsForChallenge =
            currentDocs.values
                .filter { it.challengeId == challenge.challengeId }
                .associateBy { it.wallet }
        val wallets = buildSet {
            add(challenge.creator)
            addAll(challenge.participants)
            addAll(challenge.invited)
            addAll(challenge.declined)
            addAll(docsForChallenge.keys)
        }

        wallets.forEach { wallet ->
            val recordId = B3trUserChallenge.documentId(wallet, challenge.challengeId)
            val (existing, nextVersion) = accumulator.resolve(recordId)
            val current = existing ?: currentDocs[recordId]
            val participantActions = current?.participantActions ?: BigInteger.ZERO
            val updated =
                buildUserChallengeState(
                    challenge = challenge,
                    wallet = wallet,
                    participantActions = participantActions,
                    version = nextVersion,
                    event = latestEvent,
                )

            if (current != updated) {
                accumulator.put(recordId, current, updated)
                currentDocs[recordId] = updated
            }
        }
    }

    private fun applyActionReward(
        rewardEvent: IndexedEvent,
        runtimeState: ChallengeRuntimeState,
        currentDocs: MutableMap<String, B3trUserChallenge>,
        currentChallenges: MutableMap<Long, B3trChallenge>,
        accumulator: VersionedDocumentAccumulator<B3trUserChallenge>,
    ) {
        val receiver =
            rewardEvent.params.getAsString("receiver")?.let(HexUtils::normalise) ?: return
        val appId = rewardEvent.params.getAsString("appId")?.lowercase() ?: return
        currentDocs.values
            .filter { it.wallet == receiver }
            .forEach { record ->
                val challenge =
                    getChallengeSnapshot(record.challengeId, runtimeState, currentChallenges)
                        ?: return@forEach
                val computedView =
                    computeChallengeView(challenge, receiver, record.participantActions)
                if (computedView.viewerRelation != ChallengeViewerRelation.Joined) {
                    return@forEach
                }
                if (challenge.currentRound !in challenge.startRound..challenge.endRound) {
                    return@forEach
                }
                if (
                    !challenge.allApps &&
                        challenge.selectedApps.none { it.equals(appId, ignoreCase = true) }
                ) {
                    return@forEach
                }

                val recordId = record.getDocumentId()
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val current = existing ?: currentDocs[recordId] ?: record
                val nextParticipantActions = current.participantActions + BigInteger.ONE
                val updated =
                    buildUserChallengeState(
                        challenge = challenge,
                        wallet = receiver,
                        participantActions = nextParticipantActions,
                        version = nextVersion,
                        event = rewardEvent,
                    )
                if (current != updated) {
                    accumulator.put(recordId, current, updated)
                    currentDocs[recordId] = updated
                }
            }
    }

    private fun refreshAllUserChallenges(
        latestEvent: IndexedEvent,
        runtimeState: ChallengeRuntimeState,
        currentDocs: MutableMap<String, B3trUserChallenge>,
        currentChallenges: MutableMap<Long, B3trChallenge>,
        accumulator: VersionedDocumentAccumulator<B3trUserChallenge>,
    ) {
        var page = PageRequest.of(0, 500) as org.springframework.data.domain.Pageable
        while (true) {
            val slice = repository.findAll(page)
            slice.content.forEach { stored ->
                val current = currentDocs[stored.getDocumentId()] ?: stored
                val challenge =
                    getChallengeSnapshot(current.challengeId, runtimeState, currentChallenges)
                        ?: return@forEach
                val (existing, nextVersion) = accumulator.resolve(current.getDocumentId())
                val latest = existing ?: current
                val updated =
                    buildUserChallengeState(
                        challenge = challenge,
                        wallet = current.wallet,
                        participantActions = latest.participantActions,
                        version = nextVersion,
                        event = latestEvent,
                    )
                if (latest != updated) {
                    accumulator.put(current.getDocumentId(), latest, updated)
                    currentDocs[current.getDocumentId()] = updated
                }
            }

            if (!slice.hasNext()) {
                return
            }
            page = slice.nextPageable()
        }
    }

    private fun buildUserChallengeState(
        challenge: B3trChallenge,
        wallet: String,
        participantActions: BigInteger,
        version: Int,
        event: IndexedEvent,
    ): B3trUserChallenge {
        val computedView = computeChallengeView(challenge, wallet, participantActions)
        return B3trUserChallenge(
            version = version,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            wallet = wallet,
            challengeId = challenge.challengeId,
            challengeCreatedAtBlockTimestamp = challenge.createdAtBlockTimestamp,
            viewerRelation = computedView.viewerRelation,
            availableActions = computedView.availableActions,
            participantActions = participantActions,
            isRelevant = computedView.isRelevant,
            isActionable = computedView.isActionable,
            isParticipating = computedView.isParticipating,
            isHistorical = computedView.isHistorical,
        )
    }

    private fun buildChallengeSnapshot(
        challengeId: Long,
        existing: B3trChallenge?,
        eventsForChallenge: List<IndexedEvent>,
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

        return state.toDocument(challengeId, existing?.version ?: 1, latestEvent, runtimeState)
    }

    private fun getRuntimeState(): ChallengeRuntimeState {
        if (runtimeState != null) {
            return runtimeState!!
        }
        val latestChallengeRecord = challengeRepository.getLatestRecord()
        runtimeState =
            latestChallengeRecord?.let {
                ChallengeRuntimeState(it.currentRound, it.maxParticipants)
            } ?: ChallengeRuntimeState()
        return runtimeState!!
    }

    private fun hydrateChallenges(
        challengeIds: Set<Long>,
        currentChallenges: MutableMap<Long, B3trChallenge>,
    ) {
        challengeIds.forEach { challengeId ->
            if (challengeId in currentChallenges) {
                return@forEach
            }
            challengeRepository.findByIdOrNull(B3trChallenge.documentId(challengeId))?.let {
                currentChallenges[challengeId] = it
            }
        }
    }

    private fun getChallengeSnapshot(
        challengeId: Long,
        runtimeState: ChallengeRuntimeState,
        currentChallenges: MutableMap<Long, B3trChallenge>,
    ): B3trChallenge? {
        val challenge =
            currentChallenges[challengeId]
                ?: challengeRepository.findByIdOrNull(B3trChallenge.documentId(challengeId))?.also {
                    currentChallenges[challengeId] = it
                }
                ?: return null
        return challenge.withRuntimeState(runtimeState).also { currentChallenges[challengeId] = it }
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

    private fun hasGlobalRuntimeChange(events: List<IndexedEvent>): Boolean =
        events.any {
            it.eventType == "MaxParticipantsUpdated" ||
                it.eventType == "EmissionDistributed" ||
                it.eventType == "EmissionDistributedV2"
        }

    private fun isChallengeEvent(event: IndexedEvent): Boolean =
        "challengeId" in event.params.getReturnValues()

    private fun getChallengeId(event: IndexedEvent): Long =
        when (val value = event.params.getReturnValues()["challengeId"]) {
            is Number -> value.toLong()
            else -> value?.toString()?.toLong() ?: error("Expected challengeId value")
        }
}
