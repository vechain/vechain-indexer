package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("accounts", "total-accounts")
@Deprecated("V1 total-accounts config is deprecated. Use AccountTotalsSeriesConfig instead.")
open class TotalAccountsConfig {
    @Bean
    open fun totalAccountsIndexer(
        thorClient: ThorClient,
        processor: TotalAccountsProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.TOTAL_ACCOUNTS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .build()
}
