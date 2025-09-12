package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.UserRoundActionSummary

data class AppLeaderboardItem(
    val appId: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
) {
    companion object {
        fun from(summary: UserAllTimeActionSummary): AppLeaderboardItem {
            return AppLeaderboardItem(
                appId = summary.entity,
                roundId = null,
                date = null,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: UserDailyActionSummary): AppLeaderboardItem {
            return AppLeaderboardItem(
                appId = summary.entity,
                roundId = null,
                date = summary.date,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: UserRoundActionSummary): AppLeaderboardItem {
            return AppLeaderboardItem(
                appId = summary.entity,
                roundId = summary.roundId,
                date = null,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }
    }
}
