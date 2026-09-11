package org.vechain.indexer.postgres

/** One indexer's schema as its store sees it: undo from a block on, empty for a resync, prune. */
interface PostgresIndexerTables {
    fun rollbackFrom(blockNumber: Long)

    fun truncate()

    /** Drops superseded rows older than [before]; append-only schemas have nothing to drop. */
    fun prune(before: Long) {}
}
