package org.vechain.indexer.transfer

import jakarta.annotation.PostConstruct
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

@Profile("transfers")
@Configuration
open class TransferCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedTransferEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.transfers}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = "TransferEventIndexer",
            IndexedTransferEvent::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "transfer_blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "transfer_to_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("to", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_from_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("from", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("tokenAddress", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_tokenAddress_1_eventType_1_to_1_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("tokenAddress", Sort.Direction.ASC)
                        .on("eventType", Sort.Direction.ASC)
                        .on("to", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_tokenAddress_1_eventType_1_from_1_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("tokenAddress", Sort.Direction.ASC)
                        .on("eventType", Sort.Direction.ASC)
                        .on("from", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_to_1_tokenAddress_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("to", Sort.Direction.ASC)
                        .on("tokenAddress", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
            )
        )
    }
}
