package org.vechain.indexer.contracts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("contracts", "contract")
open class ContractConfig {
    @Bean
    open fun contractIndexer(
        thorClient: ThorClient,
        processor: ContractProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.contracts:500}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.CONTRACTS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis/contract")
            .abiEventNames(listOf("\$Master"))
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .build()
}
