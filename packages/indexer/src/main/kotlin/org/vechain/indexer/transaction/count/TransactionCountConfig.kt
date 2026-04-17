package org.vechain.indexer.transaction.count

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("transactions")
open class TransactionCountConfig {
    @Bean
    open fun transactionCountIndexer(
        thorClient: ThorClient,
        processor: TransactionCountProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.TRANSACTION_COUNT.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(syncLoggerInterval)
            .startBlock(0L)
            .includeFullBlock()
            .build()
}
