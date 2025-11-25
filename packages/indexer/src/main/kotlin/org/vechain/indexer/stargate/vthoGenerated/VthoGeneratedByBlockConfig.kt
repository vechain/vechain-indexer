package org.vechain.indexer.stargate.vthoGenerated

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
import org.vechain.indexer.stargate.StargateUtils
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vtho-generated-by-block")
open class VthoGeneratedByBlockConfig {
    @Bean
    open fun vthoGeneratedByBlockArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<VthoGeneratedByBlock, VthoGeneratedByBlockArchive> =
        ArchiveService(
            mongoTemplate,
            VthoGeneratedByBlock::class.java,
            VthoGeneratedByBlockArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun vthoGeneratedAccountPruner(
        vthoGeneratedByBlockArchiveService:
            ArchiveService<VthoGeneratedByBlock, VthoGeneratedByBlockArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<VthoGeneratedByBlock, VthoGeneratedByBlockArchive> =
        PrunerService(
            VthoGeneratedByBlockArchive::class,
            vthoGeneratedByBlockArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun vthoGeneratedByBlockIndexer(
        thorClient: ThorClient,
        processor: VthoGeneratedByBlockProcessor,
        vthoGeneratedAccountPruner:
            TargetedPruner<VthoGeneratedByBlock, VthoGeneratedByBlockArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VTHO_GENERATED_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vthoGeneratedAccountPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_CLAIM_REWARDS"))
            .businessEventContracts(listOf(stargateContract, VTHO_CONTRACT_ADDRESS))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .callDataClauses(StargateUtils.buildBalanceOfClause(stargateContract))
            .includeFullBlock()
            .build()
}
