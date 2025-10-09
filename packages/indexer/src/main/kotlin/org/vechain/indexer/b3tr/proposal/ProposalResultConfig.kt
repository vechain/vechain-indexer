package org.vechain.indexer.b3tr.proposal

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
open class ProposalResultConfig {
    @Bean
    open fun proposalResultArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<ProposalResult, ProposalResultArchive> =
        ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = ProposalResult::class.java,
            archiveClazz = ProposalResultArchive::class.java,
            queryLimit = recordLimit,
        )

    @Bean
    open fun proposalResultPruner(
        proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<ProposalResult, ProposalResultArchive> =
        PrunerService(
            klass = ProposalResultArchive::class,
            archiveService = proposalResultArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun proposalResultIndexer(
        thorClient: ThorClient,
        processor: ProposalResultProcessor,
        proposalResultPruner: TargetedPruner<ProposalResult, ProposalResultArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-proposal}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_GOVERNOR_CONTRACT}")
        b3trGovernorContract: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer =
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
            .businessEventNames(listOf("B3TR_ProposalVote"))
            .businessEventContracts(listOf(b3trGovernorContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
