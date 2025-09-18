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
    val archiveObj: Class<*>? = null,
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

        // Create the archive collection if it does not exist and an archive class is provided
        if (archiveObj != null && !mongoTemplate.collectionExists(archiveObj)) {
            try {
                logger.info("⏱ Creating Archive:  ${archiveObj.simpleName}")
                mongoTemplate.createCollection(archiveObj)
                logger.info("✅ Creation Success: ${archiveObj.simpleName}.")
            } catch (e: Exception) {
                logger.error("⛔ Creation Failed:  ${archiveObj.simpleName}", e)
                throw e
            }
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
    private fun ensureIndex(indexName: String, index: Index) {
        try {
            logger.info("⏱ Creating Index:    $indexName for ${modelObj.simpleName}️")
            mongoTemplate.indexOps(modelObj).ensureIndex(index.named(indexName).background())
            logger.info("✅ Creation Success: $indexName for ${modelObj.simpleName}.")
        } catch (e: Exception) {
            logger.error("⛔ Creation Failed:  $indexName for ${modelObj.simpleName}️", e)
        }
    }

    fun ensureIndexes(indexes: Collection<Pair<String, Index>>) {
        coroutineScope.launch {
            for ((indexName, index) in indexes) {
                ensureIndex(indexName, index)
            }
        }
    }

    private fun ensureArchiveIndexes() {
        if (archiveObj == null) {
            throw RuntimeException("Archive object is null")
        }
        ensureIndexes(
            listOf(
                "blockNumber_1" to Index().on("data.blockNumber", Sort.Direction.ASC),
                "data._id_1_data.version_-1" to
                    Index()
                        .on("data._id", Sort.Direction.ASC)
                        .on("data.version", Sort.Direction.DESC),
            )
        )
    }
}
