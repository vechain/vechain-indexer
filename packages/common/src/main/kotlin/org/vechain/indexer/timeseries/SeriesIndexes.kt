package org.vechain.indexer.timeseries

import org.vechain.indexer.postgres.DeferrableIndex

/** A series table's two API-only indexes: by timestamp, and the GIN a frame page scans. */
object SeriesIndexes {

    fun of(table: String): List<DeferrableIndex> =
        listOf(
            DeferrableIndex("${table}_time_idx", table, "(block_timestamp)"),
            DeferrableIndex("${table}_frames_idx", table, "USING GIN (time_frames)"),
        )
}
