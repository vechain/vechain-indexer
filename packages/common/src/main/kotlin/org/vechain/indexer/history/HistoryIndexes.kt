package org.vechain.indexer.history

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The history indexes no indexer reads; V6 and V32 still make them on a fresh schema. */
object HistoryIndexes {
    private const val ACTIONS = "WHERE event_name = 'B3TR_ACTION'"

    val SET =
        IndexSet(
            "history",
            listOf(
                // /stargate/tokens/{id}/history
                DeferrableIndex("event_token_idx", "event", "(token_id, block_timestamp, id)"),
                DeferrableIndex(
                    "event_token_name_idx",
                    "event",
                    "(token_id, event_name, block_timestamp, id)",
                ),
                // /nfts/history
                DeferrableIndex(
                    "event_contract_token_idx",
                    "event",
                    "(contract_address, token_id, event_name, block_timestamp, id)",
                ),
                // /b3tr/actions/users/{wallet}[?appId] and /b3tr/actions/apps/{appId}
                DeferrableIndex(
                    "event_action_to_idx",
                    "event",
                    "(to_address, block_timestamp, id) $ACTIONS",
                ),
                DeferrableIndex(
                    "event_action_to_app_idx",
                    "event",
                    "(to_address, app_id, block_timestamp, id) $ACTIONS",
                ),
                DeferrableIndex(
                    "event_action_app_idx",
                    "event",
                    "(app_id, block_timestamp, id) $ACTIONS",
                ),
                // /history/{account}?eventName=
                DeferrableIndex(
                    "event_address_name_idx",
                    "event_address",
                    "(address, event_name, block_timestamp, event_id)",
                ),
            ),
        )
}
