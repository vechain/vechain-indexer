package org.vechain.indexer.b3tr.proposal

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
@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
open class ProposalResultConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.b3tr-proposal-results:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.PROPOSAL_RESULT,
            tableName = "b3tr_proposal_results",
            schemaResource = "db/tables/b3tr_proposal_results.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun proposalResultPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "b3tr_proposal_results")

    @Bean
    open fun proposalResultIndexer(
        thorClient: ThorClient,
        processor: ProposalResultProcessor,
        proposalResultPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-proposal}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_GOVERNOR_CONTRACT}")
        b3trGovernorContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.PROPOSAL_RESULT)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(proposalResultPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ProposalCreated", "B3TR_ProposalVote"))
            .businessEventContracts(listOf(b3trGovernorContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
