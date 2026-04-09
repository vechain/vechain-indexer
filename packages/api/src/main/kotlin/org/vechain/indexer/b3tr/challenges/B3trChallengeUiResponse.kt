package org.vechain.indexer.b3tr.challenges

import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.thor.model.Views
import org.web3j.utils.Convert

@JsonView(Views.Public::class)
data class B3trChallengeUiResponse(
    val challengeId: Long,
    val createdAt: Long,
    val kind: ChallengeKind,
    val visibility: ChallengeVisibility,
    val thresholdMode: ThresholdMode,
    val status: ChallengeStatus,
    val settlementMode: SettlementMode,
    val creator: String,
    val stakeAmount: String,
    val totalPrize: String,
    val startRound: Int,
    val endRound: Int,
    val duration: Int,
    val threshold: String,
    val allApps: Boolean,
    val participantCount: Int,
    val maxParticipants: Int,
    val invitedCount: Int,
    val declinedCount: Int,
    val selectedAppsCount: Int,
    val viewerStatus: ParticipantStatus,
    val isCreator: Boolean,
    val isJoined: Boolean,
    val isInvitationPending: Boolean,
    val canJoin: Boolean,
    val canLeave: Boolean,
    val canAccept: Boolean,
    val canDecline: Boolean,
    val canCancel: Boolean,
    val canAddInvites: Boolean,
    val canClaim: Boolean,
    val canRefund: Boolean,
    val canFinalize: Boolean,
) {
    companion object {
        internal fun from(
            challenge: B3trChallenge,
            state: ChallengeUiState,
        ): B3trChallengeUiResponse =
            B3trChallengeUiResponse(
                challengeId = challenge.challengeId,
                createdAt = challenge.createdAtBlockTimestamp,
                kind = challenge.kind,
                visibility = challenge.visibility,
                thresholdMode = challenge.thresholdMode,
                status = state.status,
                settlementMode = challenge.settlementMode,
                creator = challenge.creator,
                stakeAmount = formatTokenAmount(challenge.stakeAmount),
                totalPrize = formatTokenAmount(challenge.totalPrize),
                startRound = challenge.startRound,
                endRound = challenge.endRound,
                duration = challenge.duration,
                threshold = challenge.threshold.toString(),
                allApps = challenge.allApps,
                participantCount = challenge.participantCount,
                maxParticipants = state.maxParticipants,
                invitedCount = challenge.invitedCount,
                declinedCount = challenge.declinedCount,
                selectedAppsCount = challenge.selectedAppsCount,
                viewerStatus = state.viewerStatus,
                isCreator = state.isCreator,
                isJoined = state.isJoined,
                isInvitationPending = state.isInvitationPending,
                canJoin = state.canJoin,
                canLeave = state.canLeave,
                canAccept = state.canAccept,
                canDecline = state.canDecline,
                canCancel = state.canCancel,
                canAddInvites = state.canAddInvites,
                canClaim = state.canClaim,
                canRefund = state.canRefund,
                canFinalize = state.canFinalize,
            )

        private fun formatTokenAmount(value: BigInteger): String =
            Convert.fromWei(value.toString(), Convert.Unit.ETHER)
                .stripTrailingZeros()
                .toPlainString()
    }
}

internal data class ChallengeUiState(
    val status: ChallengeStatus,
    val maxParticipants: Int,
    val viewerStatus: ParticipantStatus,
    val isCreator: Boolean,
    val isJoined: Boolean,
    val isInvitationPending: Boolean,
    val canJoin: Boolean,
    val canLeave: Boolean,
    val canAccept: Boolean,
    val canDecline: Boolean,
    val canCancel: Boolean,
    val canAddInvites: Boolean,
    val canClaim: Boolean,
    val canRefund: Boolean,
    val canFinalize: Boolean,
)
