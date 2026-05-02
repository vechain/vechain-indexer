package org.vechain.indexer.transfer

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
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
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    IndexedTransferEvent::to.name to Sort.Direction.ASC,
                    IndexedTransferEvent::blockTimestamp.name to Sort.Direction.DESC,
                    IndexedTransferEvent::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedTransferEvent::from.name to Sort.Direction.ASC,
                    IndexedTransferEvent::blockTimestamp.name to Sort.Direction.DESC,
                    IndexedTransferEvent::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedTransferEvent::tokenAddress.name to Sort.Direction.ASC,
                    IndexedTransferEvent::blockTimestamp.name to Sort.Direction.DESC,
                    IndexedTransferEvent::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedTransferEvent::eventType.name to Sort.Direction.ASC,
                    IndexedTransferEvent::blockNumber.name to Sort.Direction.DESC,
                    IndexedTransferEvent::transferIndex.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
