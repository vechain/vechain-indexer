package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexerVersion

@Service
open class IndexerVersionService(
    private val mongoTemplate: MongoTemplate,
) {
    private val logger = LoggerFactory.getLogger(IndexerVersionService::class.java)

    /**
     * Checks if the version of the indexer has changed. If the version has changed, it will drop
     * and recreate the collection and update the version.
     *
     * @param collectionName The name of the collection to drop and recreate.
     * @param newVersion The new version number of the indexer.
     */
    fun checkAndResetCollectionIfVersionChanged(
        collectionName: String,
        newVersion: Int,
    ): Boolean {
        try {
            val storedVersion =
                getStoredIndexerVersion(collectionName) // Fetch current version of the indexer

            if (storedVersion < newVersion) {
                logger.info(
                    "Indexer version for $collectionName has changed. Dropping and recreating collection $collectionName.",
                )
                mongoTemplate.dropCollection(collectionName) // Drop the collection

                updateIndexerVersion(collectionName, newVersion) // Update the version
                return true // Return true indicating the collection was reset
            } else if (storedVersion == -1) {
                // Handle case where there is no version document at all
                logger.info(
                    "No version document found for $collectionName, initializing it with version 1.",
                )
                updateIndexerVersion(
                    collectionName,
                    1
                ) // Initialize with version 1 if it's a new collection
            }
            return false // Return false indicating no changes were made
        } catch (e: Exception) {
            logger.error("Error checking or resetting collection version for $collectionName", e)
            // Handle error (e.g., return false)
            return false
        }
    }

    /**
     * Drop the archive collection.
     *
     * @param archiveCollectionName The name of the archive collection to drop.
     */
    fun dropArchiveCollection(archiveCollectionName: String) {
        logger.info("Dropping archive collection $archiveCollectionName if it exists.")
        try {
            mongoTemplate.dropCollection(archiveCollectionName)
            logger.info("Successfully dropped archive collection: $archiveCollectionName.")
        } catch (e: Exception) {
            logger.warn(
                "Failed to drop archive collection: $archiveCollectionName. Exception: ${e.message}",
            )
        }
    }

    /**
     * Retrieves the current version of the indexer.
     *
     * @param indexerName The name of the indexer to fetch the version for.
     * @return The stored version number for the indexer. Returns `-1` if no versioned document is
     *   found.
     */
    private fun getStoredIndexerVersion(indexerName: String): Int {
        val indexer = mongoTemplate.findById(indexerName, IndexerVersion::class.java)
        return indexer?.version
            ?: -1 // Return -1 if no document is found, indicating no version is stored.
    }

    /**
     * Updates the version of the indexer in the indexer version collection.
     *
     * @param indexerName The name of the indexer to update.
     * @param newVersion The new version number to set for the indexer.
     */
    private fun updateIndexerVersion(
        indexerName: String,
        newVersion: Int,
    ) {
        // Create or update the version document
        val metadata = IndexerVersion(id = indexerName, version = newVersion)
        mongoTemplate.save(metadata) // Save the updated version document.
    }
}
