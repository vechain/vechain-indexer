package org.vechain.indexer.b3tr.action.response

import java.math.BigDecimal
import org.vechain.indexer.b3tr.action.EntityActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.date

/** One day of a wallet's rewards, as `/actions/users/{wallet}/daily-summaries` lists them. */
data class UserDailyActionSummary(
    val entity: String,
    val date: String,
    val actionsRewarded: Long,
    val totalRewardAmount: BigDecimal,
    val totalImpact: Impact?,
) {
    companion object {
        fun from(summary: EntityActionSummary): UserDailyActionSummary =
            UserDailyActionSummary(
                entity = summary.entity,
                date = summary.period.date!!,
                actionsRewarded = summary.actionsRewarded,
                totalRewardAmount = summary.totalRewardAmount,
                totalImpact = summary.totalImpact,
            )
    }
}
