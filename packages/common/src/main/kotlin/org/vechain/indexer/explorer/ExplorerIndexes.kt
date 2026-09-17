package org.vechain.indexer.explorer

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** The block-usage series, written on every block; the daily rollup is read back per day. */
object ExplorerIndexes {
    val SET = IndexSet("explorer", SeriesIndexes.of("block_usage"))
}
