package org.vechain.indexer.stargate.nftHolders

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
@Profile("stargate", "nft-holders-by-block")
open class NftHoldersByBlockConfig {
    @Bean
    open fun nftHoldersByBlockArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<NftHoldersByBlock, NftHoldersByBlockArchive> =
        ArchiveService(
            mongoTemplate,
            NftHoldersByBlock::class.java,
            NftHoldersByBlockArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun nftHoldersByBlockPruner(
        nftHoldersByBlockArchiveService:
            ArchiveService<NftHoldersByBlock, NftHoldersByBlockArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<NftHoldersByBlock, NftHoldersByBlockArchive> =
        PrunerService(
            NftHoldersByBlockArchive::class,
            nftHoldersByBlockArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun nftHoldersByBlockIndexer(
        thorClient: ThorClient,
        processor: NftHoldersByBlockProcessor,
        nftHoldersByBlockPruner: TargetedPruner<NftHoldersByBlock, NftHoldersByBlockArchive>,
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
            .name(IndexerNames.NFT_HOLDERS_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .prunerInterval(prunerInterval)
            .pruner(nftHoldersByBlockPruner)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_STAKE", "STARGATE_UNSTAKE"))
            .businessEventContracts(listOf(stargateNftContract, stargateDelegationContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
