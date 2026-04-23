package org.vechain.indexer.b3tr.challenges

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils

internal object B3trUserChallengeEventUtils {
    /**
     * Returns every (wallet, challengeId) pair that [event] directly mutates. `ChallengeCompleted`
     * affects every local participant and is handled separately by the service.
     */
    fun relevantWallets(event: IndexedEvent): Set<String> =
        when (event.eventType) {
            "ChallengeCreated" -> setOfNotNull(getAddressOrNull(event, "creator"))
            "ChallengeInviteAdded" -> setOfNotNull(getAddressOrNull(event, "invitee"))
            "ChallengeJoined",
            "ChallengeLeft",
            "ChallengeDeclined" -> setOfNotNull(getAddressOrNull(event, "participant"))
            "ChallengePayoutClaimed",
            "ChallengeRefundClaimed" -> setOfNotNull(getAddressOrNull(event, "account"))
            "SplitWinPrizeClaimed" -> setOfNotNull(getAddressOrNull(event, "winner"))
            "SplitWinCreatorRefunded" -> setOfNotNull(getAddressOrNull(event, "creator"))
            else -> emptySet()
        }

    fun createUserChallengeState(
        createEvent: IndexedEvent,
        wallet: String,
        challengeCreatedAtBlockTimestamp: Long,
    ): MutableUserChallengeState {
        require(createEvent.eventType == "ChallengeCreated") {
            "createUserChallengeState expects a ChallengeCreated event"
        }
        val kind = ChallengeKind.fromOrdinal(toIntValue(eventValue(createEvent, "kind")))
        val creator = getAddress(createEvent, "creator")
        val isCreator = wallet == creator

        return MutableUserChallengeState(
            wallet = wallet,
            challengeId = getChallengeId(createEvent),
            challengeCreatedAtBlockTimestamp = challengeCreatedAtBlockTimestamp,
            // Stake challenges auto-join the creator on-chain; Sponsored do not.
            participantStatus =
                if (isCreator && kind == ChallengeKind.Stake) ParticipantStatus.Joined
                else ParticipantStatus.None,
            isCreator = isCreator,
            isWinner = false,
            hasClaimedPrize = false,
            hasClaimedRefund = false,
        )
    }

    /**
     * Starts from [blank] (a not-yet-persisted record) and applies the given event. Returns the
     * updated state. Used for events where we see a wallet for the first time outside a
     * `ChallengeCreated` flow (e.g. an invitee or a public-join winner).
     */
    fun createEmptyUserChallengeState(
        wallet: String,
        challengeId: Long,
        challengeCreatedAtBlockTimestamp: Long,
    ): MutableUserChallengeState =
        MutableUserChallengeState(
            wallet = wallet,
            challengeId = challengeId,
            challengeCreatedAtBlockTimestamp = challengeCreatedAtBlockTimestamp,
            participantStatus = ParticipantStatus.None,
            isCreator = false,
            isWinner = false,
            hasClaimedPrize = false,
            hasClaimedRefund = false,
        )

    fun applyEvent(state: MutableUserChallengeState, event: IndexedEvent) {
        when (event.eventType) {
            "ChallengeCreated" -> {
                // No-op: state already constructed via createUserChallengeState.
            }
            "ChallengeInviteAdded" -> handleInviteAdded(state)
            "ChallengeJoined" -> handleJoined(state)
            "ChallengeLeft" -> handleLeft(state)
            "ChallengeDeclined" -> handleDeclined(state)
            "ChallengePayoutClaimed" -> handlePayoutClaimed(state)
            "SplitWinPrizeClaimed" -> handleSplitWinPrizeClaimed(state)
            "SplitWinCreatorRefunded" -> handleSplitWinCreatorRefunded(state)
            "ChallengeRefundClaimed" -> handleRefundClaimed(state)
            else ->
                error(
                    "Unsupported user-challenge event ${event.eventType} for " +
                        "challenge ${getChallengeId(event)}"
                )
        }
    }

    private fun handleInviteAdded(state: MutableUserChallengeState) {
        // Re-inviting a Joined participant is a no-op (matches on-chain contract: swapRemove on
        // declined, addDistinct on invited). Anything else becomes Invited.
        if (state.participantStatus != ParticipantStatus.Joined) {
            state.participantStatus = ParticipantStatus.Invited
        }
    }

    private fun handleJoined(state: MutableUserChallengeState) {
        state.participantStatus = ParticipantStatus.Joined
    }

    private fun handleLeft(state: MutableUserChallengeState) {
        // Contract: if the leaver was an eligibleInvitee, they go back to `invited` — but tracking
        // that here would require cross-entity lookups. Treat Left as None; subsequent
        // InviteAdded events (should they come) will restore Invited.
        state.participantStatus = ParticipantStatus.None
    }

    private fun handleDeclined(state: MutableUserChallengeState) {
        state.participantStatus = ParticipantStatus.Declined
    }

    private fun handlePayoutClaimed(state: MutableUserChallengeState) {
        state.hasClaimedPrize = true
        // Belt-and-braces: a successful claim implies eligibility even if we didn't run the
        // eager winner-detection at ChallengeCompleted time.
        state.isWinner = true
    }

    private fun handleSplitWinPrizeClaimed(state: MutableUserChallengeState) {
        state.isWinner = true
        state.hasClaimedPrize = true
        // A SplitWin winner may have joined a public challenge without a prior invite, so their
        // participantStatus would be None when the first event lands on this record. Reflect the
        // claim itself implying they had Joined to claim.
        if (state.participantStatus == ParticipantStatus.None) {
            state.participantStatus = ParticipantStatus.Joined
        }
    }

    private fun handleSplitWinCreatorRefunded(state: MutableUserChallengeState) {
        state.hasClaimedRefund = true
    }

    private fun handleRefundClaimed(state: MutableUserChallengeState) {
        state.hasClaimedRefund = true
    }

    fun getChallengeId(event: IndexedEvent): Long =
        when (val value = event.params.getReturnValues()["challengeId"]) {
            is Number -> value.toLong()
            else -> value?.toString()?.toLong() ?: error("Expected challengeId value")
        }

    private fun eventValue(event: IndexedEvent, key: String): Any? =
        event.params.getReturnValues()[key]

    private fun getAddress(event: IndexedEvent, key: String): String =
        HexUtils.normalise(eventValue(event, key)?.toString() ?: error("Expected address for $key"))

    private fun getAddressOrNull(event: IndexedEvent, key: String): String? =
        eventValue(event, key)?.toString()?.let(HexUtils::normalise)

    private fun toIntValue(value: Any?): Int =
        when (value) {
            is Number -> value.toInt()
            is String -> value.toBigInteger().toInt()
            else -> error("Expected numeric value, got $value")
        }
}

internal fun B3trUserChallenge.toMutableState(): MutableUserChallengeState =
    MutableUserChallengeState(
        wallet = wallet,
        challengeId = challengeId,
        challengeCreatedAtBlockTimestamp = challengeCreatedAtBlockTimestamp,
        participantStatus = participantStatus,
        isCreator = isCreator,
        isWinner = isWinner,
        hasClaimedPrize = hasClaimedPrize,
        hasClaimedRefund = hasClaimedRefund,
    )

internal fun MutableUserChallengeState.toDocument(
    version: Int,
    latestEvent: IndexedEvent,
): B3trUserChallenge =
    B3trUserChallenge(
        version = version,
        blockId = latestEvent.blockId,
        blockNumber = latestEvent.blockNumber,
        blockTimestamp = latestEvent.blockTimestamp,
        wallet = wallet,
        challengeId = challengeId,
        challengeCreatedAtBlockTimestamp = challengeCreatedAtBlockTimestamp,
        participantStatus = participantStatus,
        isCreator = isCreator,
        isWinner = isWinner,
        hasClaimedPrize = hasClaimedPrize,
        hasClaimedRefund = hasClaimedRefund,
    )

internal data class MutableUserChallengeState(
    val wallet: String,
    val challengeId: Long,
    var challengeCreatedAtBlockTimestamp: Long,
    var participantStatus: ParticipantStatus,
    var isCreator: Boolean,
    var isWinner: Boolean,
    var hasClaimedPrize: Boolean,
    var hasClaimedRefund: Boolean,
)
