package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.service.IndexerVersionService

@Profile("transfer-events")
@Configuration
open class TransferCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, IndexedTransferEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.transfers}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            "transfer_events",
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex("transfer_blockNumber_-1", Index().on("blockNumber", Sort.Direction.DESC))

        ensureIndex(
            "transfer_to_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("to", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "transfer_from_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("from", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("tokenAddress", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "transfer_tokenAddress_1_eventType_1_to_1_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("tokenAddress", Sort.Direction.ASC)
                .on("eventType", Sort.Direction.ASC)
                .on("to", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "transfer_tokenAddress_1_eventType_1_from_1_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("tokenAddress", Sort.Direction.ASC)
                .on("eventType", Sort.Direction.ASC)
                .on("from", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "transfer_to_1_tokenAddress_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("to", Sort.Direction.ASC)
                .on("tokenAddress", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )
    }
}
