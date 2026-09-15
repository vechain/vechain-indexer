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
@Profile("accounts")
open class AccountsConfig {
    /** Every block from genesis, its VET transfers and every token transfer, for the count. */
    @Bean
    open fun accountsIndexer(
        thorClient: ThorClient,
        processor: AccountsProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNTS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .includeVetTransfers()
            .abis("abis/tokens")
            .abiEventNames(listOf("Transfer", "TransferSingle", "TransferBatch"))
            .build()
}
