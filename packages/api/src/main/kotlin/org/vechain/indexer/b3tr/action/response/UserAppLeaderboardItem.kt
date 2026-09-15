package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.AppUserActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.date
import org.vechain.indexer.b3tr.action.roundId

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
        fun from(summary: AppUserActionSummary): UserAppLeaderboardItem =
            UserAppLeaderboardItem(
                appId = summary.appId,
                user = summary.user,
                roundId = summary.period.roundId,
                date = summary.period.date,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
    }
}
