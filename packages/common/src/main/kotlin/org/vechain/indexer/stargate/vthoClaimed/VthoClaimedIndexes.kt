package org.vechain.indexer.stargate.vthoClaimed

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** Only the series half; the per-token totals are read back by account on every claim. */
object VthoClaimedIndexes {
    val SET = IndexSet("stargate_vtho_claimed", SeriesIndexes.of("total_by_block"))
}
