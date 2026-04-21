package org.vechain.indexer.b3tr.challenges

data class UserChallengeRefResponse(val challengeId: Long, val createdAt: Long) {
    companion object {
        fun from(challenge: B3trUserChallenge): UserChallengeRefResponse =
            UserChallengeRefResponse(
                challengeId = challenge.challengeId,
                createdAt = challenge.challengeCreatedAtBlockTimestamp,
            )
    }
}
