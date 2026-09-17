package org.vechain.indexer.vevote

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The proposalId filter alone; three foreign keys hold the rest of the schema in place. */
object HistoricProposalsIndexes {

    val SET =
        IndexSet(
            "vevote_historic",
            listOf(DeferrableIndex("proposal_id_idx", "proposal", "(proposal_id)")),
        )
}
