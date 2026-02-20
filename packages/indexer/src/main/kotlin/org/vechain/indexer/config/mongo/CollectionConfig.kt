package org.vechain.indexer.config.mongo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter

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

        // Create archive indexes on the main collection if archives are enabled
        if (hasArchives) {
            ensureArchiveIndexesOnMainCollection()
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
            logger.info("⏱ Creating Index:    $indexName for ${entityClass.simpleName}️")
            val indexDef = index.named(indexName).background()
            if (partialFilter != null && !indexHasPartialFilter(indexDef)) {
                indexDef.partial(PartialIndexFilter.of(partialFilter))
            }
            mongoTemplate.indexOps(entityClass).ensureIndex(indexDef)
            logger.info("✅ Creation Success: $indexName for ${entityClass.simpleName}.")
        } catch (e: Exception) {
            logger.error("⛔ Creation Failed:  $indexName for ${entityClass.simpleName}️", e)
        }
    }

    private fun indexHasPartialFilter(index: Index): Boolean {
        val indexInfo = index.indexOptions
        return indexInfo.containsKey("partialFilterExpression")
    }

    fun ensureIndexes(
        indexes: Collection<Pair<String, Index>>,
        entityClass: Class<*> = modelObj,
        partialFilter: Document? = null,
    ) {
        coroutineScope.launch {
            for ((indexName, index) in indexes) {
                ensureIndex(indexName, index, entityClass, partialFilter)
            }
        }
    }

    private fun ensureArchiveIndexesOnMainCollection() {
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
            ),
            modelObj,
        )
    }
}
