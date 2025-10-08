package org.vechain.indexer.history

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("history")
@Configuration
open class HistoryCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedHistoryEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.history}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.HISTORY,
            IndexedHistoryEvent::class.java,
            version,
        )

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "appId_1_blockTimestamp_-1" to
                    Index()
                        .on("appId", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC),
                "blockNumber_1" to Index().on("blockNumber", Sort.Direction.ASC),
                "contractAddress_1" to Index().on("contractAddress", Sort.Direction.ASC),
                "to_1_contractAddress_1_blockTimestamp_-1" to
                    Index()
                        .on("to", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC),
                "from_1_contractAddress_1_blockTimestamp_-1" to
                    Index()
                        .on("from", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC),
                "origin_1_contractAddress_1_blockTimestamp_-1" to
                    Index()
                        .on("origin", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC),
                "from_1_blockTimestamp_-1_eventName_1" to
                    Index()
                        .on("from", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC),
                "from_1_blockTimestamp_-1_eventName_1_contractAddress_1" to
                    Index()
                        .on("from", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC),
                "to_1_blockTimestamp_-1_eventName_1" to
                    Index()
                        .on("to", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC),
                "to_1_blockTimestamp_-1_eventName_1_contractAddress_1" to
                    Index()
                        .on("to", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC),
                "origin_1_blockTimestamp_-1_eventName_1" to
                    Index()
                        .on("origin", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC),
                "origin_1_blockTimestamp_-1_eventName_1_contractAddress_1" to
                    Index()
                        .on("origin", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC),
                "gasPayer_1_blockTimestamp_-1_eventName_1" to
                    Index()
                        .on("gasPayer", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC)
                        .on("eventName", Sort.Direction.ASC),
                "eventName_1_to_1_blockTimestamp_-1" to
                    Index()
                        .on("eventName", Sort.Direction.ASC)
                        .on("to", Sort.Direction.ASC)
                        .on("blockTimestamp", Sort.Direction.DESC),
            )
        )
    }
}
