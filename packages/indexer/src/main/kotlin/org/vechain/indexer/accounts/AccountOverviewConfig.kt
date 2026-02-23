package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("accounts", "account-overview")
open class AccountOverviewConfig {
    @Bean
    open fun accountOverviewIndexer(
        thorClient: ThorClient,
        processor: AccountOverviewProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNT_OVERVIEW.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .includeVetTransfers()
            .abis("abis/other")
            .abiEventNames(listOf("Transfer"))
            .abiContracts(listOf(VTHO_CONTRACT_ADDRESS))
            .build()
}
