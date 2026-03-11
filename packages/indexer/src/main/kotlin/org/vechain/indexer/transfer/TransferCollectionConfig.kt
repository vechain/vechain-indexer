package org.vechain.indexer.transfer

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

@Profile("transfers", "transfers-only")
@Configuration
open class TransferCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedTransferEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.transfers}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.TRANSFER.NAME,
            IndexedTransferEvent::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "transfer_blockNumber_-1" to
                    Index().on(IndexedTransferEvent::blockNumber.name, Sort.Direction.DESC),
                "transfer_to_1_blockTimestamp_-1_txId_-1__id_-1" to
                    Index()
                        .on(IndexedTransferEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedTransferEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_from_1_blockTimestamp_-1_txId_-1__id_-1" to
                    Index()
                        .on(IndexedTransferEvent::from.name, Sort.Direction.ASC)
                        .on(IndexedTransferEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "transfer_tokenAddress_1_blockTimestamp_-1_txId_-1__id_-1" to
                    Index()
                        .on(IndexedTransferEvent::tokenAddress.name, Sort.Direction.ASC)
                        .on(IndexedTransferEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
            )
        )
    }
}
