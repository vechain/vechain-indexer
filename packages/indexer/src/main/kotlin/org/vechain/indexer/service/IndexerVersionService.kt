package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexerVersion

@Service
open class IndexerVersionService(
    private val mongoTemplate: MongoTemplate,
    private val mappingContext: MongoMappingContext,
) {
    private val logger = LoggerFactory.getLogger(IndexerVersionService::class.java)

    /**
     * Checks if the version of the indexer has changed for the given model class. If the stored
     * version is lower than the new version, the corresponding MongoDB collection will be dropped
     * and the version will be updated.
     *
     * @param clazz The model class annotated with @Document.
     * @param newVersion The new version number of the indexer.
     * @return True if the collection was dropped and version updated, false otherwise.
     */
    fun checkAndResetCollectionIfVersionChanged(clazz: Class<*>, newVersion: Int): Boolean {
        val collectionName = getCollectionName(clazz)
        try {
            val storedVersion = getStoredIndexerVersion(collectionName)

            if (storedVersion == -1) {
                logger.info("No version document found for $collectionName. No action taken.")
                updateIndexerVersion(collectionName, 1)
                return false
            }

            if (storedVersion < newVersion) {
                logger.info(
                    "Indexer version for $collectionName has changed. Dropping and recreating collection $collectionName."
                )
                mongoTemplate.dropCollection(collectionName)

                updateIndexerVersion(collectionName, newVersion)
                return true
            }

            return false
        } catch (e: Exception) {
            logger.error("Error checking or resetting collection version for $collectionName", e)
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
                "Failed to drop archive collection: $archiveCollectionName. Exception: ${e.message}"
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
        return indexer?.version ?: -1
    }

    /**
     * Updates the version of the indexer in the indexer version collection.
     *
     * @param indexerName The name of the indexer to update.
     * @param newVersion The new version number to set for the indexer.
     */
    private fun updateIndexerVersion(indexerName: String, newVersion: Int) {
        val metadata = IndexerVersion(id = indexerName, version = newVersion)
        mongoTemplate.save(metadata)
    }

    /**
     * Returns the MongoDB collection name for the given class. Falls back to the class name if
     * no @Document mapping is found.
     */
    private fun getCollectionName(clazz: Class<*>): String =
        mappingContext.getPersistentEntity(clazz)?.collection ?: clazz.simpleName
}
