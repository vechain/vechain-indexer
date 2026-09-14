package org.vechain.indexer.postgres

/** One indexer's schema as its store sees it: undo from a block on, empty for a resync, prune. */
interface PostgresIndexerTables {
    fun rollbackFrom(blockNumber: Long)

    fun truncate()

    /** Deletes superseded rows older than [before] and returns how many; append-only schemas: 0. */
    fun prune(before: Long): Int = 0
}
