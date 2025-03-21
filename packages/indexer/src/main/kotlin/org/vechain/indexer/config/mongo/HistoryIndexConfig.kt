package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedHistoryEvent

@Profile("history-events")
@Configuration
open class HistoryIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedHistoryEvent::class.java

        val blockNumberIndex = "blockNumber_1"
        val toContractAddressBlockTimestampIndex = "to_1_contractAddress_1_blockTimestamp_-1"
        val fromContractAddressBlockTimestampIndex = "from_1_contractAddress_1_blockTimestamp_-1"
        val originContractAddressBlockTimestampIndex =
            "origin_1_contractAddress_1_blockTimestamp_-1"
        val fromBlockTimestampEventNameIndex = "from_1_blockTimestamp_-1_eventName_1"
        val toBlockTimestampEventNameIndex = "to_1_blockTimestamp_-1_eventName_1"
        val originBlockTimestampEventNameIndex = "origin_1_blockTimestamp_-1_eventName_1"
        val gasPayerBlockTimestampEventNameIndex = "gasPayer_1_blockTimestamp_-1_eventName_1"

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        logger.debug("Creating index: $blockNumberIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(Index().named(blockNumberIndex).on("blockNumber", Sort.Direction.ASC))

        logger.debug("Creating index: $toContractAddressBlockTimestampIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(toContractAddressBlockTimestampIndex)
                    .on("to", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $fromContractAddressBlockTimestampIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(fromContractAddressBlockTimestampIndex)
                    .on("from", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $originContractAddressBlockTimestampIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(originContractAddressBlockTimestampIndex)
                    .on("origin", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $fromBlockTimestampEventNameIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(fromBlockTimestampEventNameIndex)
                    .on("from", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        logger.debug("Creating index: $toBlockTimestampEventNameIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(toBlockTimestampEventNameIndex)
                    .on("to", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        logger.debug("Creating index: $originBlockTimestampEventNameIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(originBlockTimestampEventNameIndex)
                    .on("origin", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        logger.debug("Creating index: $gasPayerBlockTimestampEventNameIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(gasPayerBlockTimestampEventNameIndex)
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        logger.info("Indexes for ${modelObj.simpleName} initialized successfully")
    }
}
