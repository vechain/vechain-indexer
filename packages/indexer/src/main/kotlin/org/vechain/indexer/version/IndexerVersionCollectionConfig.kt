package org.vechain.indexer.version

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

@Configuration
open class IndexerVersionCollectionConfig(private val mongoTemplate: MongoTemplate) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun ensureIndexes() {
        try {
            logger.info("⏱ Creating unique index on collectionName for IndexerVersion")
            mongoTemplate
                .indexOps(IndexerVersion::class.java)
                .ensureIndex(
                    Index()
                        .on(IndexerVersion::collectionName.name, Sort.Direction.ASC)
                        .unique()
                        .named("collectionName_1_unique")
                        .background()
                )
            logger.info("✅ Creation Success: collectionName_1_unique for IndexerVersion")
        } catch (e: Exception) {
            logger.error("⛔ Creation Failed: collectionName_1_unique for IndexerVersion", e)
        }
    }
}
