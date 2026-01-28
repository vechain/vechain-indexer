package org.vechain.indexer.b3tr.xAlloc

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-x-alloc")
open class XAllocResultConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.b3tr-x-alloc-result:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.X_ALLOC_RESULT,
            tableName = "b3tr_x_alloc_results",
            schemaResource = "db/tables/b3tr_x_alloc_results.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun xAllocResultPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "b3tr_x_alloc_results")

    @Bean
    open fun xAllocResultIndexer(
        thorClient: ThorClient,
        processor: XAllocResultProcessor,
        xAllocResultPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-x-alloc-result}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.X_ALLOC_VOTING_CONTRACT}")
        xAllocVotingContract: String,
        @Value("\${business-event.substitutions.X_ALLOC_POOL_CONTRACT}") xAllocPoolContract: String,
        @Value("\${business-event.substitutions.B3TR_DBA_POOL_CONTRACT}") dbaPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.X_ALLOC_RESULT)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(xAllocResultPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_XAllocationVote",
                    "B3TR_XAllocationRewardsClaimed",
                    "B3TR_DBAFundsDistributed",
                )
            )
            .businessEventContracts(
                listOf(xAllocVotingContract, xAllocPoolContract, dbaPoolContract)
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
