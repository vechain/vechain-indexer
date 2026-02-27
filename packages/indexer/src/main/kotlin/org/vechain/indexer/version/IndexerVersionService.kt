package org.vechain.indexer.version

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
open class IndexerVersionService(
    private val mongoTemplate: MongoTemplate,
    private val repo: IndexerVersionRepository,
    private val mappingContext: MongoMappingContext,
) {
    private val logger = LoggerFactory.getLogger(IndexerVersionService::class.java)

    /**
     * Checks if the version of the indexer has changed for the given model class. If the stored
     * version is lower than the new version, the corresponding MongoDB collection will be dropped
     * and the version will be updated.
     *
     * @param indexerName The name of the indexer.
     * @param clazz The model class annotated with @Document representing the collection.
     * @param newVersion The new version number of the indexer.
     * @return True if the collection was dropped and version updated, false otherwise.
     */
    fun checkAndResetCollectionIfVersionChanged(
        indexerName: String,
        clazz: Class<*>,
        newVersion: Int,
    ): Boolean {
        try {
            val collectionName = getCollectionName(clazz)
            val storedVersion = getStoredIndexerVersion(collectionName)

            if (storedVersion == null) {
                logger.info(
                    "No version document found for $collectionName. Creating new version document."
                )
                updateIndexerVersion(indexerName, collectionName, newVersion)
                // Do not treat this as a "reset" of the collection; it only bootstraps the version
                // document.
                return false
            }

            if (storedVersion < newVersion) {
                updateIndexerVersion(indexerName, collectionName, newVersion)

                return dropCollection(collectionName)
            }

            return false
        } catch (e: Exception) {
            logger.error("Error checking or resetting collection version for $indexerName", e)
            return false
        }
    }

    fun dropCollection(collectionName: String): Boolean {
        logger.info("Dropping collection $collectionName if it exists.")
        try {
            mongoTemplate.dropCollection(collectionName)
            logger.info("Successfully dropped collection: $collectionName.")
            return true
        } catch (e: Exception) {
            logger.warn("Failed to drop collection: $collectionName. Exception: ${e.message}")
            return false
        }
    }

    /**
     * Retrieves the current version of the indexer.
     *
     * @param collectionName The name of the indexer to fetch the version for.
     * @return The stored version number for the indexer, or `null` if no versioned document is
     *   found.
     */
    fun getStoredIndexerVersion(collectionName: String): Int? =
        repo.findByCollectionName(collectionName)?.version

    /**
     * Updates the version of the indexer in the indexer version collection.
     *
     * @param indexerName The name of the indexer to update.
     * @param newVersion The new version number to set for the indexer.
     */
    private fun updateIndexerVersion(indexerName: String, collectionName: String, newVersion: Int) {
        val updated =
            repo.findByIdOrNull(indexerName)?.copy(version = newVersion)
                ?: IndexerVersion(
                    indexerName = indexerName,
                    collectionName = collectionName,
                    version = newVersion,
                )
        repo.save(updated)
    }

    /**
     * Returns the MongoDB collection name for the given class. Falls back to the class name if
     * no @Document mapping is found.
     */
    fun getCollectionName(clazz: Class<*>): String =
        mappingContext.getPersistentEntity(clazz)?.collection
            ?: throw IllegalStateException(
                "Could not determine collection name for class ${clazz.name}"
            )
}
