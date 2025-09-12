package org.vechain.indexer.b3tr.xAlloc

import jakarta.annotation.PostConstruct
import kotlin.jvm.java
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-x-alloc")
@Configuration
open class XAllocResultCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, XAllocResult::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.b3tr-x-alloc-result}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                XAllocResult::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(XAllocResultArchive::class.java)

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "roundId_-1" to Index().on("roundId", Sort.Direction.DESC),
                "appId_-1" to Index().on("appId", Sort.Direction.DESC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
            )
        )
    }
}
