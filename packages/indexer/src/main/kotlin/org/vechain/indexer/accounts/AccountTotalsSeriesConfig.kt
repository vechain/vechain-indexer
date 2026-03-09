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
@Profile("accounts", "account-totals-series")
open class AccountTotalsSeriesConfig {
    @Bean
    open fun accountTotalsSeriesIndexer(
        thorClient: ThorClient,
        processor: AccountTotalsSeriesProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.start-block.account-totals-series:0}") startBlock: Long,
        @Value("\${indexer.sync-block-batch-size.account-totals-series:100}")
        syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNT_TOTALS_SERIES.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .build()
}
