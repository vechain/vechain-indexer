package org.vechain.indexer.version

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

@Configuration
open class IndexerVersionCollectionConfig(private val mongoTemplate: MongoTemplate) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun ensureIndexes() {
        val indexName = "collectionName_1_unique"
        val indexOps = mongoTemplate.indexOps(IndexerVersion::class.java)
        if (indexOps.indexInfo.any { it.name == indexName }) {
            logger.debug("Index $indexName already exists for IndexerVersion")
            return
        }
        try {
            logger.info("Creating unique index $indexName for IndexerVersion")
            indexOps.createIndex(
                Index()
                    .on(IndexerVersion::collectionName.name, Sort.Direction.ASC)
                    .unique()
                    .named(indexName)
                    .background()
            )
        } catch (e: Exception) {
            logger.error("Failed to create index $indexName for IndexerVersion", e)
            throw IllegalStateException("Failed to create index $indexName for IndexerVersion", e)
        }
    }
}
