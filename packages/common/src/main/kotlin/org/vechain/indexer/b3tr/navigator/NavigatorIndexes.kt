package org.vechain.indexer.b3tr.navigator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Only the delegation feed's two pages; every current-row partial is an indexer read. */
object NavigatorIndexes {

    val SET =
        IndexSet(
            "b3tr_navigator",
            listOf("navigator", "citizen").map { by ->
                DeferrableIndex(
                    "delegation_event_${by}_idx",
                    "delegation_event",
                    "($by, block_timestamp, tx_id, id)",
                )
            },
        )
}
