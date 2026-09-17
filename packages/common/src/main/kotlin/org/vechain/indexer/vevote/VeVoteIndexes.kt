package org.vechain.indexer.vevote

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Both comment pages and the by-support page; the running total is read by proposal. */
object VeVoteIndexes {

    val SET =
        IndexSet(
            "vevote",
            listOf(
                DeferrableIndex(
                    "comment_proposal_idx",
                    "comment",
                    "(proposal_id, block_number, id)",
                ),
                DeferrableIndex("comment_voter_idx", "comment", "(voter, block_number, id)"),
                DeferrableIndex(
                    "result_current_support_idx",
                    "result",
                    "(support, block_number) WHERE superseded_at IS NULL",
                ),
            ),
        )
}
