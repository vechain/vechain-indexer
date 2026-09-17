package org.vechain.indexer.b3tr.treasury

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Both time pages; append-only, and the indexer reads nothing back. */
object TreasuryTransferIndexes {

    val SET =
        IndexSet(
            "b3tr_treasury",
            listOf(
                DeferrableIndex("transfer_time_idx", "transfer", "(block_timestamp, tx_id, id)"),
                DeferrableIndex(
                    "transfer_category_time_idx",
                    "transfer",
                    "(category, block_timestamp, tx_id, id)",
                ),
            ),
        )
}
