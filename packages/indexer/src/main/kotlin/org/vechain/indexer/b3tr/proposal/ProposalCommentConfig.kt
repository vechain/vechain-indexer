package org.vechain.indexer.b3tr.proposal

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
open class ProposalCommentConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.b3tr-proposal-comments:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.PROPOSAL_COMMENT,
            tableName = "b3tr_proposal_comments",
            schemaResource = "db/tables/b3tr_proposal_comments.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun proposalCommentIndexer(
        thorClient: ThorClient,
        processor: ProposalCommentProcessor,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-proposal}") startBlock: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.B3TR_GOVERNOR_CONTRACT}")
        b3trGovernorContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.PROPOSAL_COMMENT)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ProposalVote"))
            .businessEventContracts(listOf(b3trGovernorContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
