package org.vechain.indexer.contracts

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The by-master page; the indexer reads a contract by its address alone. */
object ContractIndexes {

    val SET =
        IndexSet(
            "contracts",
            listOf(
                DeferrableIndex(
                    "state_current_master_idx",
                    "state",
                    "(master, created_on, address) WHERE superseded_at IS NULL",
                )
            ),
        )
}
