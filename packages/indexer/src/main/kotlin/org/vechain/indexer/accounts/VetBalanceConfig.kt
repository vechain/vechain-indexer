package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("accounts", "vet-balance")
open class VetBalanceConfig {
    @Bean
    open fun vetBalanceIndexer(
        thorClient: ThorClient,
        processor: VetBalanceProcessor,
        @Value("\${indexer.start-block.vet-balance}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.vet-balance}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VET_BALANCE_INDEXER)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeVetTransfers()
            .build()
}
