package org.vechain.indexer.postgres

import org.vechain.indexer.history.HistoryIndexes

/** An index only `packages/api` reads, so a backfill can do without it until the head. */
data class DeferrableIndex(val name: String, val table: String, val definition: String)

/** One schema's deferrable indexes: the truth for what a schema carries when it is serving. */
data class IndexSet(val schema: String, val indexes: List<DeferrableIndex>)

/** Every declared set, so a test database can stand a schema up the way a served one stands. */
object IndexSets {
    val ALL: List<IndexSet> = listOf(HistoryIndexes.SET)
}
