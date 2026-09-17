package org.vechain.indexer.safe

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Two pages. Five keys point at safe.proxy, so the cascade holds the rest of the schema down. */
object SafeIndexes {

    val SET =
        IndexSet(
            "safe",
            listOf(
                // /safes/owner/{address}
                DeferrableIndex(
                    "membership_current_owner_idx",
                    "membership",
                    "(owner, added_block) WHERE superseded_at IS NULL",
                ),
                // /safes/{safe}/transactions, newest first.
                DeferrableIndex(
                    "tx_proposal_current_safe_idx",
                    "tx_proposal",
                    "(safe, block_number) WHERE superseded_at IS NULL",
                ),
            ),
        )
}
