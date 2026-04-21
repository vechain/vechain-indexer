package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger

enum class ChallengePhase {
    Upcoming,
    Live,
    Ended,
}

enum class ChallengeViewerRelation {
    Creator,
    Joined,
    Invited,
    Declined,
    None,
}

enum class ChallengeAction {
    Join,
    Leave,
    AcceptInvite,
    DeclineInvite,
    Cancel,
    AddInvites,
    Claim,
    Refund,
    Complete,
    ClaimSplitWin,
    ClaimCreatorSplitWinRefund,
}

enum class UserChallengeListType {
    actionable,
    participating,
    history,
}

data class ChallengeRuntimeState(val currentRound: Int = 0, val maxParticipants: Int = 0)

data class ChallengeComputedView(
    val lifecycleStatus: ChallengeStatus,
    val phase: ChallengePhase,
    val viewerRelation: ChallengeViewerRelation,
    val availableActions: List<ChallengeAction>,
    val isActionable: Boolean,
    val isParticipating: Boolean,
    val isHistorical: Boolean,
    val isRelevant: Boolean,
)

fun computeChallengeLifecycleStatus(
    rawStatus: ChallengeStatus,
    currentRound: Int,
    startRound: Int,
    kind: ChallengeKind,
    participantCount: Int,
): ChallengeStatus {
    if (rawStatus != ChallengeStatus.Pending) {
        return rawStatus
    }

    if (currentRound < startRound) {
        return ChallengeStatus.Pending
    }

    val minimumParticipants = if (kind == ChallengeKind.Stake) 2 else 1
    return if (participantCount >= minimumParticipants) {
        ChallengeStatus.Active
    } else {
        ChallengeStatus.Invalid
    }
}

fun computeChallengePhase(currentRound: Int, startRound: Int, endRound: Int): ChallengePhase =
    when {
        currentRound < startRound -> ChallengePhase.Upcoming
        currentRound > endRound -> ChallengePhase.Ended
        else -> ChallengePhase.Live
    }

fun B3trChallenge.withRuntimeState(runtimeState: ChallengeRuntimeState): B3trChallenge {
    val lifecycleStatus =
        computeChallengeLifecycleStatus(
            rawStatus = status,
            currentRound = runtimeState.currentRound,
            startRound = startRound,
            kind = kind,
            participantCount = participantCount,
        )
    val phase =
        computeChallengePhase(
            currentRound = runtimeState.currentRound,
            startRound = startRound,
            endRound = endRound,
        )

    return if (
        this.lifecycleStatus == lifecycleStatus &&
            this.phase == phase &&
            this.currentRound == runtimeState.currentRound &&
            this.maxParticipants == runtimeState.maxParticipants
    ) {
        this
    } else {
        copy(
            lifecycleStatus = lifecycleStatus,
            phase = phase,
            currentRound = runtimeState.currentRound,
            maxParticipants = runtimeState.maxParticipants,
        )
    }
}

fun computeChallengeView(
    challenge: B3trChallenge,
    wallet: String,
    participantActions: BigInteger,
): ChallengeComputedView {
    val viewerRelation =
        when {
            challenge.creator == wallet -> ChallengeViewerRelation.Creator
            challenge.participants.contains(wallet) -> ChallengeViewerRelation.Joined
            challenge.invited.contains(wallet) -> ChallengeViewerRelation.Invited
            challenge.declined.contains(wallet) -> ChallengeViewerRelation.Declined
            else -> ChallengeViewerRelation.None
        }

    val isJoined = viewerRelation == ChallengeViewerRelation.Joined
    val isCreator = viewerRelation == ChallengeViewerRelation.Creator
    val isSplitWin = challenge.challengeType == ChallengeType.SplitWin
    val hasClaimed = challenge.claimedBy.contains(wallet)
    val hasRefunded = challenge.refundedBy.contains(wallet)
    val isSplitWinWinner = challenge.winners.contains(wallet)
    val isEligibleInvitee = challenge.eligibleInvitees.contains(wallet)
    val isInvitationPending =
        challenge.lifecycleStatus == ChallengeStatus.Pending &&
            (viewerRelation == ChallengeViewerRelation.Invited || isEligibleInvitee) &&
            !isJoined
    val hasReachedParticipantLimit =
        !isSplitWin && challenge.participantCount >= challenge.maxParticipants
    val canJoin =
        challenge.lifecycleStatus == ChallengeStatus.Pending &&
            challenge.visibility == ChallengeVisibility.Public &&
            !isJoined &&
            !isCreator &&
            !hasReachedParticipantLimit
    val canAccept = isInvitationPending && !hasReachedParticipantLimit
    val canDecline = isInvitationPending && viewerRelation != ChallengeViewerRelation.Declined
    val canLeave = challenge.lifecycleStatus == ChallengeStatus.Pending && isJoined && !isCreator
    val canCancel = challenge.lifecycleStatus == ChallengeStatus.Pending && isCreator
    val canAddInvites =
        challenge.lifecycleStatus == ChallengeStatus.Pending &&
            challenge.visibility == ChallengeVisibility.Private &&
            isCreator &&
            challenge.currentRound < challenge.startRound
    val canClaim =
        !isSplitWin &&
            !hasClaimed &&
            challenge.lifecycleStatus == ChallengeStatus.Completed &&
            when (challenge.settlementMode) {
                SettlementMode.CreatorRefund -> isCreator
                else -> isJoined && participantActions == challenge.bestScore
            }
    val slotsLeft = challenge.numWinners - challenge.winnersClaimed
    val inSplitWinWindow = challenge.currentRound in challenge.startRound..challenge.endRound
    val canClaimSplitWin =
        isSplitWin &&
            !isSplitWinWinner &&
            challenge.lifecycleStatus == ChallengeStatus.Active &&
            isJoined &&
            inSplitWinWindow &&
            slotsLeft > 0 &&
            participantActions >= challenge.threshold
    val canClaimCreatorSplitWinRefund =
        isSplitWin &&
            isCreator &&
            !hasRefunded &&
            challenge.currentRound > challenge.endRound &&
            slotsLeft > 0 &&
            (challenge.lifecycleStatus == ChallengeStatus.Active ||
                challenge.lifecycleStatus == ChallengeStatus.Completed)
    val canRefund =
        !hasRefunded &&
            (challenge.lifecycleStatus == ChallengeStatus.Cancelled ||
                challenge.lifecycleStatus == ChallengeStatus.Invalid) &&
            if (challenge.kind == ChallengeKind.Stake) {
                isJoined
            } else {
                isCreator
            }
    val isAwaitingCompletion =
        !isSplitWin &&
            challenge.lifecycleStatus == ChallengeStatus.Active &&
            challenge.endRound < challenge.currentRound
    val canComplete = isAwaitingCompletion && (isCreator || isJoined)

    val availableActions = buildList {
        if (canJoin) add(ChallengeAction.Join)
        if (canLeave) add(ChallengeAction.Leave)
        if (canAccept) add(ChallengeAction.AcceptInvite)
        if (canDecline) add(ChallengeAction.DeclineInvite)
        if (canCancel) add(ChallengeAction.Cancel)
        if (canAddInvites) add(ChallengeAction.AddInvites)
        if (canClaim) add(ChallengeAction.Claim)
        if (canRefund) add(ChallengeAction.Refund)
        if (canComplete) add(ChallengeAction.Complete)
        if (canClaimSplitWin) add(ChallengeAction.ClaimSplitWin)
        if (canClaimCreatorSplitWinRefund) add(ChallengeAction.ClaimCreatorSplitWinRefund)
    }

    val needsPastAction =
        ChallengeAction.Claim in availableActions ||
            ChallengeAction.Refund in availableActions ||
            ChallengeAction.Complete in availableActions ||
            ChallengeAction.ClaimSplitWin in availableActions ||
            ChallengeAction.ClaimCreatorSplitWinRefund in availableActions
    val isLive =
        challenge.lifecycleStatus == ChallengeStatus.Pending ||
            challenge.lifecycleStatus == ChallengeStatus.Active
    val isDone =
        challenge.lifecycleStatus == ChallengeStatus.Completed ||
            challenge.lifecycleStatus == ChallengeStatus.Cancelled ||
            challenge.lifecycleStatus == ChallengeStatus.Invalid
    val isParticipating = isLive && !isAwaitingCompletion && (isCreator || isJoined)
    val isActionable =
        needsPastAction ||
            ((ChallengeAction.AcceptInvite in availableActions ||
                ChallengeAction.DeclineInvite in availableActions) &&
                viewerRelation != ChallengeViewerRelation.Declined)
    val isHistorical =
        (viewerRelation == ChallengeViewerRelation.Declined &&
            ChallengeAction.AcceptInvite in availableActions) ||
            (isDone && (isCreator || isJoined) && !needsPastAction)
    val isRelevant =
        viewerRelation != ChallengeViewerRelation.None ||
            isActionable ||
            isParticipating ||
            isHistorical

    return ChallengeComputedView(
        lifecycleStatus = challenge.lifecycleStatus,
        phase = challenge.phase,
        viewerRelation = viewerRelation,
        availableActions = availableActions,
        isActionable = isActionable,
        isParticipating = isParticipating,
        isHistorical = isHistorical,
        isRelevant = isRelevant,
    )
}
