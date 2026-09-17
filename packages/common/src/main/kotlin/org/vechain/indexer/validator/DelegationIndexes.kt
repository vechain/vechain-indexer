package org.vechain.indexer.validator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Only the unfiltered page; the validator, token and transition lookups are all read here. */
object DelegationIndexes {

    val SET =
        IndexSet(
            "delegation",
            listOf(
                DeferrableIndex(
                    "state_current_block_idx",
                    "state",
                    "(block_number, id) WHERE superseded_at IS NULL",
                )
            ),
        )
}
