package org.vechain.indexer.stargate.vetDelegated

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** A plain series table: the indexer resumes from the newest block and rolls back by block. */
object VetDelegatedIndexes {
    val SET = IndexSet("vet_delegated", SeriesIndexes.of("total_by_block"))
}
