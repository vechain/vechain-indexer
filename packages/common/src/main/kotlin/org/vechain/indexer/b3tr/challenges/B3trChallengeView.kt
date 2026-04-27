package org.vechain.indexer.b3tr.challenges

data class ChallengeRuntimeState(val currentRound: Int = 0)

fun computeChallengeStatus(
    onChainStatus: ChallengeStatus,
    currentRound: Int,
    startRound: Int,
    kind: ChallengeKind,
    participantCount: Int,
): ChallengeStatus =
    when (onChainStatus) {
        ChallengeStatus.Cancelled -> ChallengeStatus.Cancelled
        ChallengeStatus.Invalid -> ChallengeStatus.Invalid
        ChallengeStatus.Completed -> ChallengeStatus.Completed
        ChallengeStatus.Active -> ChallengeStatus.Active
        ChallengeStatus.Pending ->
            if (currentRound < startRound) {
                ChallengeStatus.Pending
            } else {
                val minimumParticipants = if (kind == ChallengeKind.Stake) 2 else 1
                if (participantCount >= minimumParticipants) {
                    ChallengeStatus.Active
                } else {
                    ChallengeStatus.Invalid
                }
            }
    }

fun B3trChallenge.withRuntimeState(runtimeState: ChallengeRuntimeState): B3trChallenge {
    val status =
        computeChallengeStatus(
            onChainStatus = onChainStatus,
            currentRound = runtimeState.currentRound,
            startRound = startRound,
            kind = kind,
            participantCount = participantCount,
        )
    val endRoundPassed = runtimeState.currentRound > endRound

    return if (this.status == status && this.endRoundPassed == endRoundPassed) {
        this
    } else {
        copy(status = status, endRoundPassed = endRoundPassed)
    }
}
