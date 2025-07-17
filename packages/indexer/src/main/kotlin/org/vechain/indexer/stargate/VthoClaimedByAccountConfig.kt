package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.model.stargate.VthoClaimedByAccount
import org.vechain.indexer.model.stargate.VthoClaimedByAccountArchive
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.FileUtils

@Configuration
@Profile("stargate")
open class VthoClaimedByAccountConfig {
    @Bean
    open fun vthoClaimByAccountArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive> =
        ArchiveService(
            mongoTemplate,
            VthoClaimedByAccount::class.java,
            VthoClaimedByAccountArchive::class.java,
        )

    @Bean
    open fun vthoClaimByAccountPruner(
        vthoClaimByAccountArchiveService:
            ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
        @Value("\${indexer.pruner.removalChunkSize}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            VthoClaimedByAccountArchive::class,
            vthoClaimByAccountArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun vthoClaimedByAccountIndexer(
        thorClient: ThorClient,
        processor: VthoClaimedByAccountProcessor,
        vthoClaimByAccountPruner: Pruner,
        @Value("\${indexer.startBlock.stargate}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.stargate}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.stargate}") syncBlockBatchSize: Long,
        bEProperties: BusinessEventProperties,
    ): Indexer {
        val abiFiles = FileUtils.getJsonFilePaths("abis/stargate")
        return IndexerFactory()
            .name("VthoClaimedByAccountIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vthoClaimByAccountPruner)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEventFiles(FileUtils.getJsonFilePaths("business-events/stargate"))
            .businessEventNames(
                listOf("STARGATE_CLAIM_REWARDS_BASE", "STARGATE_CLAIM_REWARDS_DELEGATE")
            )
            .businessEventAbiFiles(abiFiles)
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
    }
}
