package org.vechain.indexer.transaction

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("transactions")
open class TransactionConfig {
    @Bean
    open fun transactionIndexer(
        thorClient: ThorClient,
        processor: TransactionProcessor,
        @Value("\${indexer.startBlock.transactions}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.transactions}") syncLogInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name("TransactionIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .abiBasePath("abis")
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .excludeVetTransfers()
            .includeFullBlock()
            .build()
}
