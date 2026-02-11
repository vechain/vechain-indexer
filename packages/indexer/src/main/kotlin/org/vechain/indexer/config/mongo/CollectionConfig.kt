package org.vechain.indexer.config.mongo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

abstract class CollectionConfig(
    private val mongoTemplate: MongoTemplate,
    private val coroutineScope: CoroutineScope,
    val modelObj: Class<*>,
    val hasArchives: Boolean = false,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract fun initCollection()

    fun ensureCollection() {
        // Create the collection if it does not exist
        if (!mongoTemplate.collectionExists(modelObj)) {
            try {
                logger.info("⏱ Creating Collection: ${modelObj.simpleName}")
                mongoTemplate.createCollection(modelObj)
                logger.info("✅ Creation Success:   ${modelObj.simpleName}.")
            } catch (e: Exception) {
                logger.error("⛔ Creation Failed:  ${modelObj.simpleName}", e)
                throw e
            }
        } else {
            logger.debug("Collection ${modelObj.simpleName} already exists.")
        }

        // Create archive indexes on the main collection if this is a stateful collection
        if (hasArchives) {
            ensureArchiveIndexes()
        }
    }

    /**
     * Create an index on the collection
     *
     * @param indexName The name of the index
     * @param index The index to create
     */
    private fun ensureIndex(indexName: String, index: Index, entityClass: Class<*> = modelObj) {
        try {
            logger.info("⏱ Creating Index:    $indexName for ${entityClass.simpleName}️")
            mongoTemplate.indexOps(entityClass).ensureIndex(index.named(indexName).background())
            logger.info("✅ Creation Success: $indexName for ${entityClass.simpleName}.")
        } catch (e: Exception) {
            logger.error("⛔ Creation Failed:  $indexName for ${entityClass.simpleName}️", e)
        }
    }

    fun ensureIndexes(indexes: Collection<Pair<String, Index>>, entityClass: Class<*> = modelObj) {
        coroutineScope.launch {
            for ((indexName, index) in indexes) {
                ensureIndex(indexName, index, entityClass)
            }
        }
    }

    private fun ensureArchiveIndexes() {
        ensureIndexes(
            listOf(
                "_isArchive_1_blockNumber_-1" to
                    Index()
                        .on("_isArchive", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC),
                "_isArchive_1__originalDocId_1_version_-1" to
                    Index()
                        .on("_isArchive", Sort.Direction.ASC)
                        .on("_originalDocId", Sort.Direction.ASC)
                        .on("version", Sort.Direction.DESC),
            )
        )
    }
}
