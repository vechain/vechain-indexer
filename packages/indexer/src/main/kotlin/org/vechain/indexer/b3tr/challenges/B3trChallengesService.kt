package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trChallengesService(
    private val repository: ChallengeWriteRepository,
    private val b3trRoundService: B3trRoundService,
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
            "EmissionDistributed",
            "EmissionDistributedV2",
        )

    /** The new row of each challenge touched in each block, with the roles the block moved. */
    data class Update(
        val challenges: List<B3trChallenge> = emptyList(),
        val members: List<ChallengeMember> = emptyList(),
        val apps: List<ChallengeApps> = emptyList(),
    )

    open suspend fun processEvents(events: List<IndexedEvent>): Update {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return Update()
        val groupedEvents = groupByBlock(relevantEvents)

        var currentRuntimeState = getRuntimeState(groupedEvents.keys.first().blockId)

        val challengeIds = relevantEvents.filter(::isChallengeEvent).map(::getChallengeId).toSet()
        val current = repository.findCurrent(challengeIds).associateBy { it.challengeId }
        val memberships = repository.findCurrentMembers(challengeIds)
        val states =
            current
                .mapValues { (id, challenge) ->
                    challenge.toMutableState(memberships[id].orEmpty())
                }
                .toMutableMap()

        val challenges = mutableListOf<B3trChallenge>()
        val members = mutableListOf<ChallengeMember>()
        val apps = mutableListOf<ChallengeApps>()
        // The newest row this entry has written for a challenge, which a round change refreshes.
        val latest = mutableMapOf<Long, B3trChallenge>()

        groupedEvents.forEach { (blockDetails, blockEvents) ->
            val previousRuntimeState = currentRuntimeState
            currentRuntimeState = updateRuntimeState(currentRuntimeState, blockEvents)

            blockEvents.filter(::isChallengeEvent).groupBy(::getChallengeId).forEach {
                (challengeId, eventsForChallenge) ->
                val existing = states[challengeId]
                val before = existing?.roles()
                val createdEvent = eventsForChallenge.firstOrNull {
                    it.eventType == "ChallengeCreated"
                }
                require(existing == null || createdEvent == null) {
                    "Unexpected ChallengeCreated event for existing challenge $challengeId"
                }

                val state =
                    existing
                        ?: B3trChallengeEventUtils.createChallengeState(
                            createdEvent ?: error("Missing ChallengeCreated for $challengeId")
                        )
                if (createdEvent != null) {
                    apps +=
                        ChallengeApps(
                            challengeId,
                            blockDetails.blockNumber,
                            state.selectedApps,
                        )
                }
                eventsForChallenge
                    .filterNot { it.eventType == "ChallengeCreated" }
                    .forEach { B3trChallengeEventUtils.applyEvent(challengeId, state, it) }

                states[challengeId] = state
                val row =
                    state.toDocument(
                        challengeId,
                        eventsForChallenge.last(),
                        currentRuntimeState,
                    )
                challenges += row
                latest[challengeId] = row
                members += diff(challengeId, blockDetails.blockNumber, before, state.roles())
            }

            if (currentRuntimeState.currentRound != previousRuntimeState.currentRound) {
                val refreshed =
                    refreshAffectedChallenges(
                        previousRound = previousRuntimeState.currentRound,
                        currentRound = currentRuntimeState.currentRound,
                        runtimeState = currentRuntimeState,
                        latest = latest,
                        blockEvent = blockEvents.last(),
                    )
                challenges += refreshed
                refreshed.forEach { latest[it.challengeId] = it }
            }
        }

        runtimeState = currentRuntimeState
        return Update(challenges, members, apps)
    }

    open fun invalidateRuntimeState() {
        runtimeState = null
    }

    /** The roles a block gave and the roles it took away; nothing else is written. */
    private fun diff(
        challengeId: Long,
        blockNumber: Long,
        before: Map<ChallengeMemberRole, List<String>>?,
        after: Map<ChallengeMemberRole, List<String>>,
    ): List<ChallengeMember> =
        ChallengeMemberRole.entries.flatMap { role ->
            val had = before?.get(role).orEmpty().toSet()
            val has = after[role].orEmpty().toSet()
            (has - had).map { ChallengeMember(challengeId, it, role, blockNumber, true) } +
                (had - has).map { ChallengeMember(challengeId, it, role, blockNumber, false) }
        }

    /**
     * A round boundary can turn a Pending challenge Active or Invalid and can take a challenge past
     * its end round, neither of which any event announces.
     */
    private fun refreshAffectedChallenges(
        previousRound: Int,
        currentRound: Int,
        runtimeState: ChallengeRuntimeState,
        latest: Map<Long, B3trChallenge>,
        blockEvent: IndexedEvent,
    ): List<B3trChallenge> {
        val lower = minOf(previousRound, currentRound)
        val upper = maxOf(previousRound, currentRound)
        val stored = repository.findCurrentByRounds(lower, upper)
        // A challenge this entry created has no stored row yet, so the query cannot return it.
        val affected =
            (stored.map { latest[it.challengeId] ?: it } +
                    latest.values.filter { movedBy(it, lower, upper) })
                .distinctBy { it.challengeId }

        return affected.mapNotNull { challenge ->
            val refreshed = challenge.withRuntimeState(runtimeState)
            if (refreshed == challenge) null
            else
                refreshed.copy(
                    blockId = blockEvent.blockId,
                    blockNumber = blockEvent.blockNumber,
                    blockTimestamp = blockEvent.blockTimestamp,
                )
        }
    }

    /** The bounds `findCurrentByRounds` selects on, so both sources of a row agree on the set. */
    private fun movedBy(challenge: B3trChallenge, lower: Int, upper: Int): Boolean =
        (challenge.startRound > lower && challenge.startRound <= upper) ||
            (challenge.endRound >= lower && challenge.endRound < upper)

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
        val latestRoundEvent = events.lastOrNull {
            it.eventType == "EmissionDistributed" || it.eventType == "EmissionDistributedV2"
        }

        return ChallengeRuntimeState(
            currentRound = latestRoundEvent?.let(ActionSummaryUtils::getCycle) ?: state.currentRound
        )
    }

    private suspend fun getRuntimeState(blockId: String): ChallengeRuntimeState {
        runtimeState?.let {
            return it
        }

        runtimeState = ChallengeRuntimeState(currentRound = restoreCurrentRound(blockId))
        return runtimeState!!
    }

    private suspend fun restoreCurrentRound(blockId: String): Int =
        b3trRoundService.getCurrentRound(blockId) ?: 0
}

/** The address lists of a challenge as the roles their members hold. */
internal fun MutableChallengeState.roles(): Map<ChallengeMemberRole, List<String>> =
    mapOf(
        ChallengeMemberRole.PARTICIPANT to participants.toList(),
        ChallengeMemberRole.INVITED to invited.toList(),
        ChallengeMemberRole.DECLINED to declined.toList(),
        ChallengeMemberRole.WINNER to winners.toList(),
        ChallengeMemberRole.ELIGIBLE_INVITEE to eligibleInvitees.toList(),
        ChallengeMemberRole.CLAIMED to claimedBy.toList(),
        ChallengeMemberRole.REFUNDED to refundedBy.toList(),
    )
