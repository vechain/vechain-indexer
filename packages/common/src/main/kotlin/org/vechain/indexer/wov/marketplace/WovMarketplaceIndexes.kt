package org.vechain.indexer.wov.marketplace

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The buyer page's seek to a running row by timestamp; the indexer seeds by block, on the key. */
object WovMarketplaceIndexes {

    val SET =
        IndexSet(
            "wov_marketplace",
            listOf(
                DeferrableIndex(
                    "buyer_running_ts_idx",
                    "buyer_running",
                    "(buyer, payment_token, block_timestamp) INCLUDE (items, spend)",
                )
            ),
        )
}
