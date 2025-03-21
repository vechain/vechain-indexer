package org.vechain.indexer.config.mongo

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

abstract class CollectionConfig(
    private val mongoTemplate: MongoTemplate,
    val modelObj: Class<*>,
    private val archiveObj: Class<*>? = null
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract fun initCollection()

    fun ensureCollection() {
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
        } else if (archiveObj != null) {
            logger.debug("Collection ${archiveObj.simpleName} already exists.")
        }
    }

    /**
     * Create an index on the collection
     *
     * @param indexName The name of the index
     * @param index The index to create
     * @param archive Whether to create the index on the archive collection
     */
    fun ensureIndex(indexName: String, index: Index, archive: Boolean = false) {
        (if (archive) archiveObj else modelObj)?.let {
            logger.debug("Creating index: $indexName for ${it.simpleName}")
            mongoTemplate.indexOps(it).ensureIndex(index.named(indexName).background())
        }
    }
}
