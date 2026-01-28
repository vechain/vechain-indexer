package org.vechain.indexer.transfer

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("transfers")
open class TransferConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.transfers:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.TRANSFER,
            tableName = "transfer_events",
            schemaResource = "db/tables/transfer_events.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun transferIndexer(
        thorClient: ThorClient,
        processor: TransferProcessor,
        @Value("\${indexer.start-block.transfers}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.transfers}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.TRANSFER)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis/tokens")
            .abiEventNames(listOf("Transfer", "TransferSingle", "TransferBatch"))
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeVetTransfers()
            .build()
}
