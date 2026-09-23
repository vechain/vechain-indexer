package org.vechain.indexer.validator

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** The unfiltered page and the VET-delegated series; the delegation lookups are read here. */
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
            ) + SeriesIndexes.of("total_by_block"),
        )
}
