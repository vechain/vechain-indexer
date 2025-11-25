package org.vechain.indexer.stargate.vetDelegated

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vet-delegated-by-block")
open class VetDelegatedByBlockConfig {
    @Bean
    open fun vetDelegatedByBlockArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<VetDelegatedByBlock, VetDelegatedByBlockArchive> =
        ArchiveService(
            mongoTemplate,
            VetDelegatedByBlock::class.java,
            VetDelegatedByBlockArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun vetDelegatedByBlockPruner(
        vetDelegatedByBlockArchiveService:
            ArchiveService<VetDelegatedByBlock, VetDelegatedByBlockArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<VetDelegatedByBlock, VetDelegatedByBlockArchive> =
        PrunerService(
            VetDelegatedByBlockArchive::class,
            vetDelegatedByBlockArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun vetDelegatedByBlockIndexer(
        thorClient: ThorClient,
        processor: VetDelegatedByBlockProcessor,
        vetDelegatedByBlockPruner: TargetedPruner<VetDelegatedByBlock, VetDelegatedByBlockArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VET_DELEGATED_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .prunerInterval(prunerInterval)
            .pruner(vetDelegatedByBlockPruner)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/stargate")
            .abiContracts(listOf(stargateContract))
            .abiEventNames(listOf("DelegationInitiated", "DelegationWithdrawn"))
            .excludeVetTransfers()
            .build()
}
