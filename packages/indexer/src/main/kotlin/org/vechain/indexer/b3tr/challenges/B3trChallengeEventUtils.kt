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
            challengeType =
                ChallengeType.fromOrdinal(toIntValue(eventValue(createEvent, "challengeType"))),
            status = ChallengeStatus.Pending,
            settlementMode = SettlementMode.None,
            creator = creator,
            title = stringValue(eventValue(createEvent, "title")),
            description = stringValue(eventValue(createEvent, "description")),
            imageURI = stringValue(eventValue(createEvent, "imageURI")),
            metadataURI = stringValue(eventValue(createEvent, "metadataURI")),
            stakeAmount = stakeAmount,
            startRound = toIntValue(eventValue(createEvent, "startRound")),
            endRound = toIntValue(eventValue(createEvent, "endRound")),
            threshold = toBigIntegerValue(eventValue(createEvent, "threshold")),
            numWinners = 0,
            winnersClaimed = 0,
            prizePerWinner = BigInteger.ZERO,
            allApps = toBooleanValue(eventValue(createEvent, "allApps")),
            totalPrize = stakeAmount,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
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
            winners = mutableListOf(),
            eligibleInvitees = mutableListOf(),
            claimedBy = mutableListOf(),
            refundedBy = mutableListOf(),
            creatorRefunded = false,
            createdAtBlockNumber = createEvent.blockNumber,
            createdAtBlockTimestamp = createEvent.blockTimestamp,
            createdTxId = createEvent.txId,
        )
    }

    fun applyEvent(challengeId: Long, state: MutableChallengeState, event: IndexedEvent) {
        when (event.eventType) {
            "SplitWinConfigured" -> handleSplitWinConfigured(state, event)
            "ChallengeInviteAdded" -> handleInviteAdded(state, event)
            "ChallengeJoined" -> handleJoined(state, event)
            "ChallengeLeft" -> handleLeft(state, event)
            "ChallengeDeclined" -> handleDeclined(state, event)
            "ChallengeCancelled" -> setStatus(state, ChallengeStatus.Cancelled)
            "ChallengeActivated" -> setStatus(state, ChallengeStatus.Active)
            "ChallengeInvalidated" -> setStatus(state, ChallengeStatus.Invalid)
            "ChallengeCompleted" -> handleCompleted(state, event)
            "ChallengePayoutClaimed" -> handlePayoutClaimed(state, event)
            "SplitWinPrizeClaimed" -> handleSplitWinPrizeClaimed(state, event)
            "SplitWinCreatorRefunded" -> handleSplitWinCreatorRefunded(state)
            "ChallengeRefundClaimed" -> handleRefundClaimed(state, event)
            else -> error("Unsupported challenge event ${event.eventType} for $challengeId")
        }
    }

    private fun handleSplitWinConfigured(state: MutableChallengeState, event: IndexedEvent) {
        state.numWinners = toIntValue(eventValue(event, "numWinners"))
        state.prizePerWinner = toBigIntegerValue(eventValue(event, "prizePerWinner"))
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

    private fun handleCompleted(state: MutableChallengeState, event: IndexedEvent) {
        state.status = ChallengeStatus.Completed
        state.settlementMode =
            SettlementMode.fromOrdinal(toIntValue(eventValue(event, "settlementMode")))
        state.bestScore = toBigIntegerValue(eventValue(event, "bestScore"))
        state.bestCount = toIntValue(eventValue(event, "bestCount"))
    }

    private fun handlePayoutClaimed(state: MutableChallengeState, event: IndexedEvent) {
        addDistinct(state.claimedBy, getAddress(event, "account"))
        state.payoutsClaimed++
    }

    private fun handleSplitWinPrizeClaimed(state: MutableChallengeState, event: IndexedEvent) {
        val winner = getAddress(event, "winner")
        if (addDistinct(state.winners, winner)) {
            state.winnersClaimed++
        }
        addDistinct(state.claimedBy, winner)
    }

    private fun handleSplitWinCreatorRefunded(state: MutableChallengeState) {
        state.creatorRefunded = true
        // Mark Completed if not already (post-endRound creator refund finalises a Split Win
        // challenge).
        if (state.status == ChallengeStatus.Active) {
            state.status = ChallengeStatus.Completed
            state.settlementMode = SettlementMode.SplitWinCompleted
        }
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
        challengeType = challengeType,
        status = status,
        settlementMode = settlementMode,
        creator = creator,
        title = title,
        description = description,
        imageURI = imageURI,
        metadataURI = metadataURI,
        stakeAmount = stakeAmount,
        startRound = startRound,
        endRound = endRound,
        threshold = threshold,
        numWinners = numWinners,
        winnersClaimed = winnersClaimed,
        prizePerWinner = prizePerWinner,
        allApps = allApps,
        totalPrize = totalPrize,
        bestScore = bestScore,
        bestCount = bestCount,
        payoutsClaimed = payoutsClaimed,
        participants = participants.toMutableList(),
        invited = invited.toMutableList(),
        declined = declined.toMutableList(),
        selectedApps = selectedApps,
        winners = winners.toMutableList(),
        eligibleInvitees = eligibleInvitees.toMutableList(),
        claimedBy = claimedBy.toMutableList(),
        refundedBy = refundedBy.toMutableList(),
        creatorRefunded = creatorRefunded,
        createdAtBlockNumber = createdAtBlockNumber,
        createdAtBlockTimestamp = createdAtBlockTimestamp,
        createdTxId = createdTxId,
    )

internal fun MutableChallengeState.toDocument(
    challengeId: Long,
    version: Int,
    latestEvent: IndexedEvent,
    runtimeState: ChallengeRuntimeState,
) =
    B3trChallenge(
        version = version,
        blockId = latestEvent.blockId,
        blockNumber = latestEvent.blockNumber,
        blockTimestamp = latestEvent.blockTimestamp,
        challengeId = challengeId,
        kind = kind,
        visibility = visibility,
        challengeType = challengeType,
        status = status,
        lifecycleStatus =
            computeChallengeLifecycleStatus(
                rawStatus = status,
                currentRound = runtimeState.currentRound,
                startRound = startRound,
                kind = kind,
                participantCount = participants.size,
            ),
        phase =
            computeChallengePhase(
                currentRound = runtimeState.currentRound,
                startRound = startRound,
                endRound = endRound,
            ),
        settlementMode = settlementMode,
        creator = creator,
        title = title,
        description = description,
        imageURI = imageURI,
        metadataURI = metadataURI,
        stakeAmount = stakeAmount,
        startRound = startRound,
        endRound = endRound,
        duration = endRound - startRound + 1,
        threshold = threshold,
        numWinners = numWinners,
        winnersClaimed = winnersClaimed,
        prizePerWinner = prizePerWinner,
        allApps = allApps,
        totalPrize = totalPrize,
        participantCount = participants.size,
        invitedCount = invited.size,
        declinedCount = declined.size,
        selectedAppsCount = selectedApps.size,
        winnersCount = winners.size,
        bestScore = bestScore,
        bestCount = bestCount,
        payoutsClaimed = payoutsClaimed,
        participants = participants.toList(),
        invited = invited.toList(),
        declined = declined.toList(),
        selectedApps = selectedApps,
        winners = winners.toList(),
        eligibleInvitees = eligibleInvitees.toList(),
        claimedBy = claimedBy.toList(),
        refundedBy = refundedBy.toList(),
        creatorRefunded = creatorRefunded,
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

private fun stringValue(value: Any?): String = value?.toString() ?: ""

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
    var challengeType: ChallengeType,
    var status: ChallengeStatus,
    var settlementMode: SettlementMode,
    var creator: String,
    var title: String,
    var description: String,
    var imageURI: String,
    var metadataURI: String,
    var stakeAmount: BigInteger,
    var startRound: Int,
    var endRound: Int,
    var threshold: BigInteger,
    var numWinners: Int,
    var winnersClaimed: Int,
    var prizePerWinner: BigInteger,
    var allApps: Boolean,
    var totalPrize: BigInteger,
    var bestScore: BigInteger,
    var bestCount: Int,
    var payoutsClaimed: Int,
    val participants: MutableList<String>,
    val invited: MutableList<String>,
    val declined: MutableList<String>,
    val selectedApps: List<String>,
    val winners: MutableList<String>,
    val eligibleInvitees: MutableList<String>,
    val claimedBy: MutableList<String>,
    val refundedBy: MutableList<String>,
    var creatorRefunded: Boolean,
    val createdAtBlockNumber: Long,
    val createdAtBlockTimestamp: Long,
    val createdTxId: String,
)
