package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.EntityActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.date
import org.vechain.indexer.b3tr.action.roundId

data class AppLeaderboardItem(
    val appId: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
) {
    companion object {
        fun from(summary: EntityActionSummary): AppLeaderboardItem =
            AppLeaderboardItem(
                appId = summary.entity,
                roundId = summary.period.roundId,
                date = summary.period.date,
                totalRewardAmount = summary.totalRewardAmount.toDouble(),
                actionsRewarded = summary.actionsRewarded,
                totalImpact = summary.totalImpact,
            )
    }
}
