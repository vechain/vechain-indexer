package org.vechain.indexer.transfer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("transfers")
open class TransferConfig {
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
