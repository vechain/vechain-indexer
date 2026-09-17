package org.vechain.indexer.b3tr.xAlloc

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** A round's apps and one app's rounds; the indexer names both halves of the key. */
object XAllocResultIndexes {

    val SET =
        IndexSet(
            "b3tr_x_alloc",
            listOf(
                current("result_current_round_idx", "(round_id)"),
                current("result_current_app_idx", "(app_id, round_id)"),
            ),
        )

    private fun current(name: String, columns: String) =
        DeferrableIndex(name, "result", "$columns WHERE superseded_at IS NULL")
}
