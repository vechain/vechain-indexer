package org.vechain.indexer.stargate.vthoGenerated

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** A plain series table: the indexer resumes from the newest block and rolls back by block. */
object VthoGeneratedIndexes {
    val SET = IndexSet("stargate_vtho_generated", SeriesIndexes.of("total_by_block"))
}
