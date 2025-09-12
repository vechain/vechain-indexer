package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.UserRoundActionSummary

data class UserLeaderboardItem(
    val wallet: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
) {
    companion object {
        fun from(summary: UserAllTimeActionSummary): UserLeaderboardItem {
            return UserLeaderboardItem(
                wallet = summary.entity,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                roundId = null,
                date = null,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: UserDailyActionSummary): UserLeaderboardItem {
            return UserLeaderboardItem(
                wallet = summary.entity,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                roundId = null,
                date = summary.date,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: UserRoundActionSummary): UserLeaderboardItem {
            return UserLeaderboardItem(
                wallet = summary.entity,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                roundId = summary.roundId,
                date = null,
                totalImpact = summary.totalImpact,
            )
        }
    }
}
