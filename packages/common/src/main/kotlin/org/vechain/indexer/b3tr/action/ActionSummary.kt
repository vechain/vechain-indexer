package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.vechain.indexer.b3tr.shared.EntityType

/** The tables one rollup lives in, and the column that tells one period from the next. */
enum class ActionPeriodKind(
    val entityTable: String,
    val appUserTable: String,
    val keyColumn: String?,
) {
    ALL_TIME("entity_all_time", "app_user_all_time", null),
    DAILY("entity_daily", "app_user_daily", "date"),
    ROUND("entity_round", "app_user_round", "round_id"),
}

/** The window a summary covers: everything so far, one UTC day, or one B3TR round. */
sealed interface ActionPeriod {
    val kind: ActionPeriodKind

    data object AllTime : ActionPeriod {
        override val kind
            get() = ActionPeriodKind.ALL_TIME
    }

    /** [date] is ISO `yyyy-MM-dd`, as the API takes and renders it. */
    data class Day(val date: String) : ActionPeriod {
        override val kind
            get() = ActionPeriodKind.DAILY
    }

    data class Round(val roundId: Int) : ActionPeriod {
        override val kind
            get() = ActionPeriodKind.ROUND
    }

    companion object {
        fun of(roundId: Int?, date: String?): ActionPeriod =
            when {
                roundId != null -> Round(roundId)
                date != null -> Day(date)
                else -> AllTime
            }
    }
}

val ActionPeriod.roundId: Int?
    get() = (this as? ActionPeriod.Round)?.roundId

val ActionPeriod.date: String?
    get() = (this as? ActionPeriod.Day)?.date

/** A row of either rollup, identified by the window it covers and the block it was current at. */
sealed interface ActionSummaryRow {
    val period: ActionPeriod
    val blockNumber: Long
}

/**
 * What one entity has been rewarded over [period], as of [blockNumber]. [uniqueUsers] is the
 * wallets an app has rewarded, or every rewarded wallet on the GLOBAL row.
 */
data class EntityActionSummary(
    val entityType: EntityType,
    val entity: String,
    override val period: ActionPeriod,
    val blockId: String,
    override val blockNumber: Long,
    val blockTimestamp: Long,
    val actionsRewarded: Long,
    val totalRewardAmount: BigDecimal,
    val totalImpact: Impact?,
    val uniqueUsers: Long = 0,
) : ActionSummaryRow

/** What one wallet has been rewarded on one app over [period], as of [blockNumber]. */
data class AppUserActionSummary(
    val appId: String,
    val user: String,
    override val period: ActionPeriod,
    val blockId: String,
    override val blockNumber: Long,
    val blockTimestamp: Long,
    val actionsRewarded: Long,
    val totalRewardAmount: BigDecimal,
    val totalImpact: Impact?,
) : ActionSummaryRow

/** Everything one entry adds to the schema. */
data class ActionSummaryUpdate(
    val entities: List<EntityActionSummary> = emptyList(),
    val appUsers: List<AppUserActionSummary> = emptyList(),
) {
    fun isEmpty(): Boolean = entities.isEmpty() && appUsers.isEmpty()
}

/** The running total a leaderboard is ordered and ranked on; names the column for SQL. */
enum class ActionSortField(val column: String, val parameter: String) {
    TOTAL_REWARD_AMOUNT("total_reward_amount", "totalRewardAmount"),
    ACTIONS_REWARDED("actions_rewarded", "actionsRewarded");

    /** The value as the column's type, so the comparison stays on the index. */
    fun argument(value: BigDecimal): Any = if (this == ACTIONS_REWARDED) value.toLong() else value

    fun valueOf(summary: EntityActionSummary): String =
        if (this == ACTIONS_REWARDED) summary.actionsRewarded.toString()
        else summary.totalRewardAmount.toPlainString()

    fun valueOf(summary: AppUserActionSummary): String =
        if (this == ACTIONS_REWARDED) summary.actionsRewarded.toString()
        else summary.totalRewardAmount.toPlainString()

    companion object {
        fun fromParameter(parameter: String): ActionSortField? = entries.firstOrNull {
            it.parameter == parameter
        }
    }
}
