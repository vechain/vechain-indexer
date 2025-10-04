package org.vechain.indexer.version

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.BlockIdentifier

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
                logger.info("No version document found for $collectionName. Creating new version document.")
                updateIndexerVersion(indexerName, collectionName, newVersion)
                return true
            }

            if (storedVersion < newVersion) {
                logger.info(
                    "Indexer version for $collectionName has changed. Dropping and recreating collection $collectionName."
                )
                mongoTemplate.dropCollection(collectionName)

                updateIndexerVersion(indexerName, collectionName, newVersion)
                return true
            }

            return false
        } catch (e: Exception) {
            logger.error("Error checking or resetting collection version for $indexerName", e)
            return false
        }
    }

    /**
     * Drop the archive collection.
     *
     * @param clazz The archive model class annotated with @Document.
     */
    fun dropArchiveCollection(clazz: Class<*>) {
        val archiveCollectionName = getCollectionName(clazz)
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
     * @param collectionName The name of the indexer to fetch the version for.
     * @return The stored version number for the indexer, or `null` if no versioned document is found.
     */
    fun getStoredIndexerVersion(collectionName: String): Int? =
        repo.findByCollectionName(collectionName)?.version

    fun getLastProcessedBlock(indexerName: String): BlockIdentifier? {
        val indexer = repo.findByIdOrNull(indexerName)
        return indexer?.lastProcessedBlock
    }

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
        mongoTemplate.save(updated)
    }

    fun updateLastSafeSyncedBlock(indexerName: String, block: BlockIdentifier?) {
        val indexer = repo.findByIdOrNull(indexerName)
        if (indexer != null) {
            val updatedIndexer = indexer.copy(lastProcessedBlock = block)
            mongoTemplate.save(updatedIndexer)
        } else {
            logger.warn(
                "No indexer version document found for $indexerName to update lastProcessedBlock."
            )
        }
    }

    /**
     * Returns the MongoDB collection name for the given class. Falls back to the class name if
     * no @Document mapping is found.
     */
    private fun getCollectionName(clazz: Class<*>): String =
        mappingContext.getPersistentEntity(clazz)?.collection
            ?: throw IllegalStateException(
                "Could not determine collection name for class ${clazz.name}"
            )
}
