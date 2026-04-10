package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils

internal object B3trChallengeEventUtils {
    fun createChallengeState(createEvent: IndexedEvent): MutableChallengeState {
        val kind = ChallengeKind.fromOrdinal(toIntValue(eventValue(createEvent, "kind")))
        val creator = getAddress(createEvent, "creator")
        val stakeAmount = toBigIntegerValue(eventValue(createEvent, "stakeAmount"))

        return MutableChallengeState(
            kind = kind,
            visibility =
                ChallengeVisibility.fromOrdinal(toIntValue(eventValue(createEvent, "visibility"))),
            thresholdMode =
                ThresholdMode.fromOrdinal(toIntValue(eventValue(createEvent, "thresholdMode"))),
            status = ChallengeStatus.Pending,
            settlementMode = SettlementMode.None,
            creator = creator,
            stakeAmount = stakeAmount,
            startRound = toIntValue(eventValue(createEvent, "startRound")),
            endRound = toIntValue(eventValue(createEvent, "endRound")),
            threshold = toBigIntegerValue(eventValue(createEvent, "threshold")),
            allApps = toBooleanValue(eventValue(createEvent, "allApps")),
            totalPrize = stakeAmount,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            qualifiedCount = 0,
            payoutsClaimed = 0,
            participants =
                if (kind == ChallengeKind.Stake) {
                    mutableListOf(creator)
                } else {
                    mutableListOf()
                },
            invited = mutableListOf(),
            declined = mutableListOf(),
            selectedApps = stringList(eventValue(createEvent, "selectedApps")),
            eligibleInvitees = mutableListOf(),
            claimedBy = mutableListOf(),
            refundedBy = mutableListOf(),
            createdAtBlockNumber = createEvent.blockNumber,
            createdAtBlockTimestamp = createEvent.blockTimestamp,
            createdTxId = createEvent.txId,
        )
    }

    fun applyEvent(challengeId: Long, state: MutableChallengeState, event: IndexedEvent) {
        when (event.eventType) {
            "ChallengeInviteAdded" -> handleInviteAdded(state, event)
            "ChallengeJoined" -> handleJoined(state, event)
            "ChallengeLeft" -> handleLeft(state, event)
            "ChallengeDeclined" -> handleDeclined(state, event)
            "ChallengeCancelled" -> setStatus(state, ChallengeStatus.Cancelled)
            "ChallengeActivated" -> setStatus(state, ChallengeStatus.Active)
            "ChallengeInvalidated" -> setStatus(state, ChallengeStatus.Invalid)
            "ChallengeFinalized" -> handleFinalized(state, event)
            "ChallengePayoutClaimed" -> handlePayoutClaimed(state, event)
            "ChallengeRefundClaimed" -> handleRefundClaimed(state, event)
            else -> error("Unsupported challenge event ${event.eventType} for $challengeId")
        }
    }

    private fun handleInviteAdded(state: MutableChallengeState, event: IndexedEvent) {
        val invitee = getAddress(event, "invitee")
        swapRemove(state.declined, invitee)
        addDistinct(state.invited, invitee)
        addDistinct(state.eligibleInvitees, invitee)
    }

    private fun handleJoined(state: MutableChallengeState, event: IndexedEvent) {
        val participant = getAddress(event, "participant")
        swapRemove(state.invited, participant)
        swapRemove(state.declined, participant)
        if (addDistinct(state.participants, participant)) {
            adjustStakePrize(state, state.stakeAmount)
        }
    }

    private fun handleLeft(state: MutableChallengeState, event: IndexedEvent) {
        val participant = getAddress(event, "participant")
        if (swapRemove(state.participants, participant)) {
            adjustStakePrize(state, state.stakeAmount.negate())
        }
        if (participant in state.eligibleInvitees) {
            addDistinct(state.invited, participant)
        }
    }

    private fun handleDeclined(state: MutableChallengeState, event: IndexedEvent) {
        val participant = getAddress(event, "participant")
        if (swapRemove(state.participants, participant)) {
            adjustStakePrize(state, state.stakeAmount.negate())
        }
        swapRemove(state.invited, participant)
        swapRemove(state.declined, participant)
        addDistinct(state.declined, participant)
        addDistinct(state.eligibleInvitees, participant)
    }

    private fun setStatus(state: MutableChallengeState, status: ChallengeStatus) {
        state.status = status
    }

    private fun handleFinalized(state: MutableChallengeState, event: IndexedEvent) {
        state.status = ChallengeStatus.Finalized
        state.settlementMode =
            SettlementMode.fromOrdinal(toIntValue(eventValue(event, "settlementMode")))
        state.bestScore = toBigIntegerValue(eventValue(event, "bestScore"))
        state.bestCount = toIntValue(eventValue(event, "bestCount"))
        state.qualifiedCount = toIntValue(eventValue(event, "qualifiedCount"))
    }

    private fun handlePayoutClaimed(state: MutableChallengeState, event: IndexedEvent) {
        addDistinct(state.claimedBy, getAddress(event, "account"))
        state.payoutsClaimed++
    }

    private fun handleRefundClaimed(state: MutableChallengeState, event: IndexedEvent) {
        addDistinct(state.refundedBy, getAddress(event, "account"))
    }

    private fun adjustStakePrize(state: MutableChallengeState, delta: BigInteger) {
        if (state.kind == ChallengeKind.Stake) {
            state.totalPrize += delta
        }
    }
}

internal fun B3trChallenge.toMutableState() =
    MutableChallengeState(
        kind = kind,
        visibility = visibility,
        thresholdMode = thresholdMode,
        status = status,
        settlementMode = settlementMode,
        creator = creator,
        stakeAmount = stakeAmount,
        startRound = startRound,
        endRound = endRound,
        threshold = threshold,
        allApps = allApps,
        totalPrize = totalPrize,
        bestScore = bestScore,
        bestCount = bestCount,
        qualifiedCount = qualifiedCount,
        payoutsClaimed = payoutsClaimed,
        participants = participants.toMutableList(),
        invited = invited.toMutableList(),
        declined = declined.toMutableList(),
        selectedApps = selectedApps,
        eligibleInvitees = eligibleInvitees.toMutableList(),
        claimedBy = claimedBy.toMutableList(),
        refundedBy = refundedBy.toMutableList(),
        createdAtBlockNumber = createdAtBlockNumber,
        createdAtBlockTimestamp = createdAtBlockTimestamp,
        createdTxId = createdTxId,
    )

internal fun MutableChallengeState.toDocument(
    challengeId: Long,
    version: Int,
    latestEvent: IndexedEvent,
) =
    B3trChallenge(
        version = version,
        blockId = latestEvent.blockId,
        blockNumber = latestEvent.blockNumber,
        blockTimestamp = latestEvent.blockTimestamp,
        challengeId = challengeId,
        kind = kind,
        visibility = visibility,
        thresholdMode = thresholdMode,
        status = status,
        settlementMode = settlementMode,
        creator = creator,
        stakeAmount = stakeAmount,
        startRound = startRound,
        endRound = endRound,
        duration = endRound - startRound + 1,
        threshold = threshold,
        allApps = allApps,
        totalPrize = totalPrize,
        participantCount = participants.size,
        invitedCount = invited.size,
        declinedCount = declined.size,
        selectedAppsCount = selectedApps.size,
        bestScore = bestScore,
        bestCount = bestCount,
        qualifiedCount = qualifiedCount,
        payoutsClaimed = payoutsClaimed,
        participants = participants.toList(),
        invited = invited.toList(),
        declined = declined.toList(),
        selectedApps = selectedApps,
        eligibleInvitees = eligibleInvitees.toList(),
        claimedBy = claimedBy.toList(),
        refundedBy = refundedBy.toList(),
        createdAtBlockNumber = createdAtBlockNumber,
        createdAtBlockTimestamp = createdAtBlockTimestamp,
        createdTxId = createdTxId,
    )

private fun eventValue(event: IndexedEvent, key: String): Any? = event.params.getReturnValues()[key]

private fun getAddress(event: IndexedEvent, key: String): String =
    normaliseAddress(eventValue(event, key))

private fun addDistinct(addresses: MutableList<String>, address: String): Boolean {
    if (address in addresses) return false
    addresses.add(address)
    return true
}

private fun swapRemove(addresses: MutableList<String>, address: String): Boolean {
    val index = addresses.indexOf(address)
    if (index == -1) return false

    val lastIndex = addresses.lastIndex
    if (index != lastIndex) {
        addresses[index] = addresses[lastIndex]
    }
    addresses.removeAt(lastIndex)
    return true
}

private fun stringList(value: Any?): List<String> =
    (value as? List<*>)?.mapNotNull { it?.toString() }?.distinct() ?: emptyList()

private fun normaliseAddress(value: Any?): String {
    val address = value?.toString() ?: error("Expected address value")
    return HexUtils.normalise(address)
}

private fun toBooleanValue(value: Any?): Boolean =
    value as? Boolean ?: error("Expected boolean value, got $value")

private fun toBigIntegerValue(value: Any?): BigInteger =
    when (value) {
        is BigInteger -> value
        is Number -> BigInteger.valueOf(value.toLong())
        is String -> value.toBigInteger()
        else -> error("Expected numeric value, got $value")
    }

private fun toIntValue(value: Any?): Int = toBigIntegerValue(value).toInt()

internal data class MutableChallengeState(
    var kind: ChallengeKind,
    var visibility: ChallengeVisibility,
    var thresholdMode: ThresholdMode,
    var status: ChallengeStatus,
    var settlementMode: SettlementMode,
    var creator: String,
    var stakeAmount: BigInteger,
    var startRound: Int,
    var endRound: Int,
    var threshold: BigInteger,
    var allApps: Boolean,
    var totalPrize: BigInteger,
    var bestScore: BigInteger,
    var bestCount: Int,
    var qualifiedCount: Int,
    var payoutsClaimed: Int,
    val participants: MutableList<String>,
    val invited: MutableList<String>,
    val declined: MutableList<String>,
    val selectedApps: List<String>,
    val eligibleInvitees: MutableList<String>,
    val claimedBy: MutableList<String>,
    val refundedBy: MutableList<String>,
    val createdAtBlockNumber: Long,
    val createdAtBlockTimestamp: Long,
    val createdTxId: String,
)
