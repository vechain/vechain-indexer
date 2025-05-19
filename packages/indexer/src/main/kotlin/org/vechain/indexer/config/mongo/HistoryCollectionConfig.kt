package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.service.IndexerVersionService

@Profile("history-events")
@Configuration
open class HistoryCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, IndexedHistoryEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.history}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged("history_events", version)

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex("blockNumber_1", Index().on("blockNumber", Sort.Direction.ASC))

        ensureIndex("contractAddress_1", Index().on("contractAddress", Sort.Direction.ASC))

        ensureIndex("isBlacklisted_1", Index().on("isBlacklisted", Sort.Direction.ASC))

        ensureIndex(
            "to_1_contractAddress_1_blockTimestamp_-1",
            Index()
                .on("to", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC),
        )

        ensureIndex(
            "from_1_contractAddress_1_blockTimestamp_-1",
            Index()
                .on("from", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC),
        )

        ensureIndex(
            "origin_1_contractAddress_1_blockTimestamp_-1",
            Index()
                .on("origin", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC),
        )

        ensureIndex(
            "from_1_blockTimestamp_-1_eventName_1",
            Index()
                .on("from", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC)
                .on("eventName", Sort.Direction.ASC),
        )

        ensureIndex(
            "to_1_blockTimestamp_-1_eventName_1",
            Index()
                .on("to", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC)
                .on("eventName", Sort.Direction.ASC),
        )

        ensureIndex(
            "origin_1_blockTimestamp_-1_eventName_1",
            Index()
                .on("origin", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC)
                .on("eventName", Sort.Direction.ASC),
        )

        ensureIndex(
            "gasPayer_1_blockTimestamp_-1_eventName_1",
            Index()
                .on("gasPayer", Sort.Direction.ASC)
                .on("blockTimestamp", Sort.Direction.DESC)
                .on("eventName", Sort.Direction.ASC),
        )
    }
}
