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
@Profile("transfers", "fungible-token-interactions")
open class FungibleTokenInteractionsConfig {
    @Bean
    open fun fungibleTokenInteractionsIndexer(
        thorClient: ThorClient,
        processor: FungibleTokenInteractionsProcessor,
        @Value("\${indexer.start-block.transfers}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.transfers}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis/fungible-tokens")
            .abiEventNames(listOf("Transfer"))
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .excludeVetTransfers()
            .build()
}
