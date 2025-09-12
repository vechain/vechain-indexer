package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.b3tr.action.Impact

data class UserAppLeaderboardItem(
    val appId: String,
    val user: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
) {
    companion object {
        fun from(summary: AppAllTimeActionSummary): UserAppLeaderboardItem {
            return UserAppLeaderboardItem(
                appId = summary.appId,
                user = summary.user,
                roundId = null,
                date = null,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: AppDailyActionSummary): UserAppLeaderboardItem {
            return UserAppLeaderboardItem(
                appId = summary.appId,
                user = summary.user,
                roundId = null,
                date = summary.date,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }

        fun from(summary: AppRoundActionSummary): UserAppLeaderboardItem {
            return UserAppLeaderboardItem(
                appId = summary.appId,
                user = summary.user,
                roundId = summary.roundId,
                date = null,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
        }
    }
}
