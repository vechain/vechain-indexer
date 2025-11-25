package org.vechain.indexer.stargate.vetStaked

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vet-staked-by-block")
open class VetStakedByBlockConfig {
    @Bean
    open fun vetStakedByBlockArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<VetStakedByBlock, VetStakedByBlockArchive> =
        ArchiveService(
            mongoTemplate,
            VetStakedByBlock::class.java,
            VetStakedByBlockArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun vetStakedByBlockPruner(
        vetStakedByBlockArchiveService: ArchiveService<VetStakedByBlock, VetStakedByBlockArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<VetStakedByBlock, VetStakedByBlockArchive> =
        PrunerService(
            VetStakedByBlockArchive::class,
            vetStakedByBlockArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun vetStakedByBlockIndexer(
        thorClient: ThorClient,
        processor: VetStakedByBlockProcessor,
        vetStakedByBlockPruner: TargetedPruner<VetStakedByBlock, VetStakedByBlockArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VET_STAKED_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vetStakedByBlockPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_STAKE", "STARGATE_UNSTAKE"))
            .businessEventContracts(listOf(stargateNftContract, stargateDelegationContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
