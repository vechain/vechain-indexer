package org.vechain.indexer.b3tr.challenges

enum class ChallengePhase {
    Upcoming,
    Live,
    Ended,
}

data class ChallengeRuntimeState(val currentRound: Int = 0, val maxParticipants: Int = 0)

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
