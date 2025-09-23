package org.vechain.indexer.b3tr.gm

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-gm-nft")
open class GmNftConfig {

    @Bean
    open fun gmNftArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<GmNft, GmNftArchive> =
        ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = GmNft::class.java,
            archiveClazz = GmNftArchive::class.java,
            queryLimit = recordLimit,
        )

    @Bean
    open fun gmNftPruner(
        gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): PrunerService<GmNft, GmNftArchive> =
        PrunerService(
            klass = GmNftArchive::class,
            archiveService = gmNftArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun gmNftIndexer(
        thorClient: ThorClient,
        processor: GmNftProcessor,
        gmNftPruner: PrunerService<GmNft, GmNftArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContractAddress: String,
        @Value("\${business-event.substitutions.VOTER_REWARDS_CONTRACT}")
        voterRewardsContractAddress: String,
        @Value("\${business-event.substitutions.GM_NFT_CONTRACT}") gmNftContractAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("GmNftIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(gmNftPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_GmTransfer",
                    "B3TR_GmBurned",
                    "B3TR_GmMinted",
                    "B3TR_GmNodeAttached",
                    "B3TR_GmNodeDetached",
                    "B3TR_GmUpgrade",
                    "B3TR_GmNodeLevel",
                )
            )
            .businessEventContracts(
                listOf(b3trContractAddress, voterRewardsContractAddress, gmNftContractAddress)
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
