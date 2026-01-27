package org.vechain.indexer.pruner

import org.vechain.indexer.Pruner

/**
 * PostgreSQL-specific pruner interface for versioned tables.
 *
 * Unlike MongoDB's TargetedPruner which requires Archive<T> type parameter, this interface works
 * with PostgreSQL's versioned-rows pattern where archives are stored in the same table with
 * is_current=false.
 */
interface PostgresTargetedPruner : Pruner {
    /**
     * Prunes old non-current versions from the table.
     *
     * @param currentBlockNumber The current block being processed
     * @param entityIds Optional list of specific entity IDs to prune (for targeted pruning)
     */
    fun run(currentBlockNumber: Long, entityIds: List<String>?)
}
