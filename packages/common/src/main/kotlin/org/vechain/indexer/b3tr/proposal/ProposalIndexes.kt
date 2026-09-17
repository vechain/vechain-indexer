package org.vechain.indexer.b3tr.proposal

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The creation page and both comment pages; the state page is how the indexer finds open ones. */
object ProposalIndexes {

    val SET =
        IndexSet(
            "b3tr_proposal",
            listOf(
                DeferrableIndex(
                    "result_current_created_idx",
                    "result",
                    "(created_at_block_number, proposal_id) WHERE superseded_at IS NULL",
                ),
                DeferrableIndex(
                    "comment_proposal_idx",
                    "comment",
                    "(proposal_id, block_number, voter)",
                ),
                DeferrableIndex(
                    "comment_voter_idx",
                    "comment",
                    "(voter, block_number, proposal_id)",
                ),
            ),
        )
}
