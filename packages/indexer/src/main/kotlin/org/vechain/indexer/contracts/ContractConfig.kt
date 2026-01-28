package org.vechain.indexer.contracts

import jakarta.annotation.PostConstruct
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
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("contracts", "contract")
open class ContractConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.contracts:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.CONTRACTS_INDEXER,
            tableName = "contracts",
            schemaResource = "db/tables/contracts.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun contractPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "contracts")

    @Bean
    open fun contractIndexer(
        thorClient: ThorClient,
        processor: ContractProcessor,
        contractPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.contracts:500}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.CONTRACTS_INDEXER)
            .thorClient(thorClient)
            .pruner(contractPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .abis("abis/contract")
            .abiEventNames(listOf("\$Master"))
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .build()
}
