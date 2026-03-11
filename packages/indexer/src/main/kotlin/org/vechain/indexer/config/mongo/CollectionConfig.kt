package org.vechain.indexer.config.mongo

import kotlinx.coroutines.CoroutineScope
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter

abstract class CollectionConfig(
    private val mongoTemplate: MongoTemplate,
    @Suppress("UNUSED_PARAMETER") _coroutineScope: CoroutineScope,
    val modelObj: Class<*>,
) {
    companion object {
        /**
         * Default partial filter for IndexedDocument collections. Excludes checkpoint documents.
         */
        val INDEXED_DOCUMENT_PARTIAL_FILTER: Document =
            Document("blockNumber", Document("\$exists", true))
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract fun initCollection()

    fun ensureCollection() {
        // Create the collection if it does not exist
        if (!mongoTemplate.collectionExists(modelObj)) {
            try {
                logger.info("Creating Collection: ${modelObj.simpleName}")
                mongoTemplate.createCollection(modelObj)
                logger.info("Creation Success:   ${modelObj.simpleName}.")
            } catch (e: Exception) {
                logger.error("Creation Failed:  ${modelObj.simpleName}", e)
                throw e
            }
        } else {
            logger.debug("Collection ${modelObj.simpleName} already exists.")
        }
    }

    /**
     * Create an index on the collection
     *
     * @param indexName The name of the index
     * @param index The index to create
     */
    private fun ensureIndex(
        indexName: String,
        index: Index,
        entityClass: Class<*> = modelObj,
        partialFilter: Document? = null,
    ) {
        try {
            logger.info("Creating Index:    $indexName for ${entityClass.simpleName}")
            val indexDef = index.named(indexName).background()
            if (partialFilter != null) {
                indexDef.partial(PartialIndexFilter.of(partialFilter))
            }
            mongoTemplate.indexOps(entityClass).ensureIndex(indexDef)
            logger.info("Creation Success: $indexName for ${entityClass.simpleName}.")
        } catch (e: Exception) {
            logger.error("Creation Failed:  $indexName for ${entityClass.simpleName}", e)
            throw IllegalStateException(
                "Failed to create index $indexName for ${entityClass.simpleName}",
                e,
            )
        }
    }

    fun ensureIndexes(
        indexes: Collection<Pair<String, Index>>,
        entityClass: Class<*> = modelObj,
        partialFilter: Document? = INDEXED_DOCUMENT_PARTIAL_FILTER,
    ) {
        if (indexes.isEmpty()) {
            logger.info("No indexes configured for ${entityClass.simpleName}.")
            return
        }

        logger.info(
            "Ensuring ${indexes.size} indexes for ${entityClass.simpleName} before startup continues"
        )

        for ((indexName, index) in indexes) {
            ensureIndex(indexName, index, entityClass, partialFilter)
        }

        logger.info("Finished ensuring indexes for ${entityClass.simpleName}.")
    }
}
