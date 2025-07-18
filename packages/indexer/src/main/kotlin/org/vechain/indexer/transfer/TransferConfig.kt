package org.vechain.indexer.transfer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("transfers")
open class TransferConfig {
    @Bean
    open fun transferIndexer(
        thorClient: ThorClient,
        processor: TransferProcessor,
        @Value("\${indexer.startBlock.transfers}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.transfers}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.transfers}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("TransferIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .abiBasePath("abis/tokens")
            .abiEventNames(listOf("Transfer", "TransferSingle", "TransferBatch"))
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .build()
}
