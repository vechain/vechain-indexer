package org.vechain.indexer.version

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.BlockIdentifier

@Service
open class IndexerVersionService(private val repo: IndexerVersionRepository) {
    private val logger = LoggerFactory.getLogger(IndexerVersionService::class.java)

    /**
     * Retrieves the current version of the indexer by table name.
     *
     * @param tableName The name of the table to fetch the version for.
     * @return The stored version number for the indexer, or `null` if no versioned document is
     *   found.
     */
    fun getStoredIndexerVersion(tableName: String): Int? = repo.findByTableName(tableName)?.version

    /**
     * Gets the last processed block for an indexer.
     *
     * @param indexerName The name of the indexer.
     * @return The last processed block identifier, or null if not found.
     */
    fun getLastProcessedBlock(indexerName: String): BlockIdentifier? {
        val indexer = repo.findById(indexerName)
        return indexer?.lastProcessedBlock
    }

    /**
     * Updates the version of the indexer.
     *
     * @param indexerName The name of the indexer to update.
     * @param tableName The database table name for this indexer.
     * @param newVersion The new version number to set for the indexer.
     */
    fun updateIndexerVersion(indexerName: String, tableName: String, newVersion: Int) {
        val updated =
            repo.findById(indexerName)?.copy(version = newVersion, lastProcessedBlock = null)
                ?: IndexerVersion(
                    indexerName = indexerName,
                    tableName = tableName,
                    version = newVersion,
                    lastProcessedBlock = null,
                )
        repo.save(updated)
        logger.info("Updated indexer version for $indexerName to $newVersion")
    }

    /**
     * Updates the last safely synced block for an indexer.
     *
     * @param indexerName The name of the indexer.
     * @param block The block identifier to store.
     */
    fun updateLastSafeSyncedBlock(indexerName: String, block: BlockIdentifier?) {
        if (block == null) return
        val indexer = repo.findById(indexerName)
        if (indexer != null) {
            val updatedIndexer = indexer.copy(lastProcessedBlock = block)
            repo.save(updatedIndexer)
        }
    }
}
