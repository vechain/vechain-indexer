package org.vechain.indexer.history

import org.vechain.indexer.postgres.ConcurrentIndex

/** The `findActions` indexes V32 declares, for the builder that makes them on a populated table. */
object HistoryActionIndexes {
    const val SCHEMA = "history"
    private const val ACTIONS = "WHERE event_name = 'B3TR_ACTION'"

    val INDEXES =
        listOf(
            ConcurrentIndex(
                "event_action_to_idx",
                "event",
                "(to_address, block_timestamp, id) $ACTIONS",
            ),
            ConcurrentIndex(
                "event_action_to_app_idx",
                "event",
                "(to_address, app_id, block_timestamp, id) $ACTIONS",
            ),
            ConcurrentIndex(
                "event_action_app_idx",
                "event",
                "(app_id, block_timestamp, id) $ACTIONS",
            ),
        )
}
