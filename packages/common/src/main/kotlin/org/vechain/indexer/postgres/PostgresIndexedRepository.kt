package org.vechain.indexer.postgres

import org.vechain.indexer.thor.model.BlockIdentifier

/**
 * Common interface for PostgreSQL-based indexed repositories.
 *
 * Defines the operations required by [BasePostgresProcessor] for block synchronization and rollback
 * support.
 */
interface PostgresIndexedRepository {
    /** Rolls back all data at or after the specified block number. */
    fun rollback(blockNumber: Long)

    /** Returns the latest block identifier from the repository, or null if empty. */
    fun getLatestBlockIdentifier(): BlockIdentifier?
}
