package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote-results")
open class VeVoteResultConfig {
    @Bean
    open fun vevoteResultsArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<VeVoteProposalResults, VeVoteProposalResultsArchive> =
        ArchiveService(
            mongoTemplate,
            VeVoteProposalResults::class.java,
            VeVoteProposalResultsArchive::class.java,
        )

    @Bean
    open fun vevoteResultsPruner(
        vevoteResultsArchiveService:
            ArchiveService<VeVoteProposalResults, VeVoteProposalResultsArchive>,
        @Value("\${indexer.pruner.removalChunkSize}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            VeVoteProposalResultsArchive::class,
            vevoteResultsArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun vevoteResultIndexer(
        thorClient: ThorClient,
        processor: VeVoteResultProcessor,
        vevoteResultsPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.startBlock.vevote}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.vevote}") syncLogInterval: Long,
        @Value("\${business-event.substitutions.VEVOTE_CONTRACT}") contractAddress: String,
        @Value("\${indexer.syncBlockBatchSize.vevote}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("VeVoteResultIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vevoteResultsPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/vevote")
            .abiContracts(listOf(contractAddress))
            .abiEventNames(listOf("VoteCast"))
            .excludeVetTransfers()
            .build()
}
