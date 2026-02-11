package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote", "vevote-results")
open class VeVoteResultConfig {

    @Bean
    open fun veVoteResultArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = VeVoteProposalResult::class.java,
            archiveClazz = VeVoteProposalResultArchive::class.java,
            queryLimit = recordLimit,
        )
    }

    @Bean
    open fun veVoteResultPruner(
        veVoteResultArchiveService:
            ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<VeVoteProposalResult, VeVoteProposalResultArchive> =
        PrunerService(
            klass = VeVoteProposalResultArchive::class,
            archiveService = veVoteResultArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun vevoteResultIndexer(
        thorClient: ThorClient,
        processor: VeVoteResultProcessor,
        veVoteResultPruner: TargetedPruner<VeVoteProposalResult, VeVoteProposalResultArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.vevote}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.VEVOTE_CONTRACT}") contractAddress: String,
        @Value("\${indexer.sync-block-batch-size.vevote}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VEVOTE_RESULT.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(veVoteResultPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/vevote")
            .abiContracts(listOf(contractAddress))
            .abiEventNames(listOf("VoteCast"))
            .excludeVetTransfers()
            .build()
}
