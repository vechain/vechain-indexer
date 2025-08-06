package org.vechain.indexer.config.mongo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

abstract class CollectionConfig(
    private val mongoTemplate: MongoTemplate,
    val modelObj: Class<*>,
    val archiveObj: Class<*>? = null,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract fun initCollection()

    fun ensureCollection() {
        if (archiveObj != null) {
            logger.info(
                "Initializing collection ${modelObj.simpleName} and archive ${archiveObj.simpleName}"
            )
        } else {
            logger.info("Initializing collection ${modelObj.simpleName}")
        }

        // Create the collection if it does not exist
        if (!mongoTemplate.collectionExists(modelObj)) {
            logger.info("Collection ${modelObj.simpleName} does not exist. Creating...")
            mongoTemplate.createCollection(modelObj)
        } else {
            logger.debug("Collection ${modelObj.simpleName} already exists.")
        }

        // Create the archive collection if it does not exist and an archive class is provided
        if (archiveObj != null && !mongoTemplate.collectionExists(archiveObj)) {
            logger.info("Archive collection ${archiveObj.simpleName} does not exist. Creating...")
            mongoTemplate.createCollection(archiveObj)
            ensureArchiveIndexes()
        } else if (archiveObj != null) {
            logger.debug("Collection ${archiveObj.simpleName} already exists.")
        }
    }

    /**
     * Create an index on the collection
     *
     * @param indexName The name of the index
     * @param index The index to create
     */
    fun ensureIndex(indexName: String, index: Index) {
        try {
            logger.info("⏱ Creating Index:    $indexName for ${modelObj.simpleName}️")
            mongoTemplate.indexOps(modelObj).ensureIndex(index.named(indexName).background())
            logger.info("✅ Creation Success: $indexName for ${modelObj.simpleName}.")
        } catch (e: Exception) {
            logger.error("⛔ Creation Failed:  $indexName for ${modelObj.simpleName}️", e)
        }
    }

    fun ensureIndexesAsync(indexes: Collection<Pair<String, Index>>) {
        CoroutineScope(Dispatchers.IO).launch {
            for ((indexName, index) in indexes) {
                ensureIndex(indexName, index)
            }
        }
    }

    private fun ensureArchiveIndexes() {
        if (archiveObj == null) {
            throw RuntimeException("Archive object is null")
        }
        logger.info("Creating index: blockNumber_1 for ${archiveObj.simpleName}")
        mongoTemplate
            .indexOps(archiveObj)
            .ensureIndex(
                Index()
                    .on("data.blockNumber", Sort.Direction.ASC)
                    .named("blockNumber_1")
                    .background()
            )
    }
}
