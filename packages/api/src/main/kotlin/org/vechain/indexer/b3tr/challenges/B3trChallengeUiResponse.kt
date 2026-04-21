package org.vechain.indexer.b3tr.challenges

data class UserChallengeStateResponse(
    val challengeId: Long,
    val createdAt: Long,
    val viewerRelation: ChallengeViewerRelation,
    val availableActions: List<ChallengeAction>,
    val participantActions: String,
    val isActionable: Boolean,
    val isParticipating: Boolean,
    val isHistorical: Boolean,
) {
    companion object {
        fun from(challenge: B3trUserChallenge): UserChallengeStateResponse =
            UserChallengeStateResponse(
                challengeId = challenge.challengeId,
                createdAt = challenge.challengeCreatedAtBlockTimestamp,
                viewerRelation = challenge.viewerRelation,
                availableActions = challenge.availableActions,
                participantActions = challenge.participantActions.toString(),
                isActionable = challenge.isActionable,
                isParticipating = challenge.isParticipating,
                isHistorical = challenge.isHistorical,
            )
    }
}
