package org.vechain.indexer.b3tr.gm

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The level overview's count; the indexer reads a GM token by its id alone. */
object GmNftIndexes {

    val SET =
        IndexSet(
            "b3tr_gm",
            listOf(
                DeferrableIndex(
                    "state_held_level_idx",
                    "state",
                    "(level) WHERE superseded_at IS NULL " +
                        "AND owner <> '\\x0000000000000000000000000000000000000000'::BYTEA",
                )
            ),
        )
}
