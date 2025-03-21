package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedTransferEvent

@Profile("transfer-events")
@Configuration
open class TransferIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedTransferEvent::class.java

        val transferBlockNumberIndex = "transfer_blockNumber_-1"
        val transferToBlockNumberTxIdIdIndex = "transfer_to_1_blockNumber_-1_txId_-1__id_-1"
        val transferFromBlockNumberTxIdIdIndex = "transfer_from_1_blockNumber_-1_txId_-1__id_-1"
        val transferTokenAddressBlockNumberTxIdIdIndex =
            "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1"
        val transferTokenAddressEventTypeToBlockNumberTxIdIdIndex =
            "transfer_tokenAddress_1_eventType_1_to_1_1_blockNumber_-1_txId_-1__id_-1"
        val transferTokenAddressEventTypeFromBlockNumberTxIdIdIndex =
            "transfer_tokenAddress_1_eventType_1_from_1_1_blockNumber_-1_txId_-1__id_-1"

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        logger.debug("Creating index: $transferBlockNumberIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index().named(transferBlockNumberIndex).on("blockNumber", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $transferToBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(transferToBlockNumberTxIdIdIndex)
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $transferFromBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(transferFromBlockNumberTxIdIdIndex)
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $transferTokenAddressBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(transferTokenAddressBlockNumberTxIdIdIndex)
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $transferTokenAddressEventTypeToBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(transferTokenAddressEventTypeToBlockNumberTxIdIdIndex)
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $transferTokenAddressEventTypeFromBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(transferTokenAddressEventTypeFromBlockNumberTxIdIdIndex)
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.info("Indexes for ${modelObj.simpleName} initialized successfully")
    }
}
