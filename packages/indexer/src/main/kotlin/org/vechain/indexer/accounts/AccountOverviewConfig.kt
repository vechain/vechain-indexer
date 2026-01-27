package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("accounts", "account-overview")
open class AccountOverviewConfig {
    @Bean
    open fun accountOverviewPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "account_overviews")

    @Bean
    open fun accountOverviewIndexer(
        thorClient: ThorClient,
        processor: AccountOverviewProcessor,
        accountOverviewPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNT_OVERVIEW_INDEXER)
            .thorClient(thorClient)
            .pruner(accountOverviewPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .includeVetTransfers()
            .build()
}
