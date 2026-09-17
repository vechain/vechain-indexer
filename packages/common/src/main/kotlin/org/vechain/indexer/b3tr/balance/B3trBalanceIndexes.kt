package org.vechain.indexer.b3tr.balance

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The three richlist orderings; the indexer only ever reads an address's own balance. */
object B3trBalanceIndexes {

    val SET =
        IndexSet(
            "b3tr_balance",
            listOf("total", "vot3", "b3tr").map { balance ->
                DeferrableIndex(
                    "state_holders_${balance}_idx",
                    "state",
                    "(${balance}_balance DESC, address) " +
                        "WHERE superseded_at IS NULL AND ${balance}_balance > 0",
                )
            },
        )
}
