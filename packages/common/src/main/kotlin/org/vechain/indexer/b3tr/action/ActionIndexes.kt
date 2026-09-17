package org.vechain.indexer.b3tr.action

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** b3tr_action's leaderboards and wallet lookups; the indexer only ever reads a row by its key. */
object ActionIndexes {

    val SET =
        IndexSet(
            "b3tr_action",
            listOf(
                    rank("entity_all_time", "entity_type", "entity"),
                    rank("entity_daily", "entity_type, date", "entity"),
                    rank("entity_round", "entity_type, round_id", "entity"),
                    rank("app_user_all_time", "app_id", "wallet"),
                    rank("app_user_daily", "app_id, date", "wallet"),
                    rank("app_user_round", "app_id, round_id", "wallet"),
                )
                .flatten() +
                // The apps one wallet has used, the one shape not keyed by the app.
                listOf(
                    current("app_user_all_time_wallet_idx", "app_user_all_time", "(wallet)"),
                    current("app_user_daily_wallet_idx", "app_user_daily", "(wallet, date)"),
                    current("app_user_round_wallet_idx", "app_user_round", "(wallet, round_id)"),
                ),
        )

    /** The two leaderboards of a table: most actions and most rewarded, within one period. */
    private fun rank(table: String, within: String, entity: String) =
        listOf("actions" to "actions_rewarded", "reward" to "total_reward_amount").map {
            (by, column) ->
            current("${table}_${by}_idx", table, "($within, $column DESC, $entity)")
        }

    private fun current(name: String, table: String, columns: String) =
        DeferrableIndex(name, table, "$columns WHERE superseded_at IS NULL")
}
