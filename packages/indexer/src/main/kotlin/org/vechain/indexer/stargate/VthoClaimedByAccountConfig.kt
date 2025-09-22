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
import org.vechain.indexer.contracts.Contants.VTHO_CONTRACT
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vtho-claimed-by-account")
open class VthoClaimedByAccountConfig {
    @Bean
    open fun vthoClaimByAccountArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive> =
        ArchiveService(
            mongoTemplate,
            VthoClaimedByAccount::class.java,
            VthoClaimedByAccountArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun vthoClaimByAccountPruner(
        vthoClaimByAccountArchiveService:
            ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
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
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.stargate}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("VthoClaimedByAccountIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vthoClaimByAccountPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(
                listOf(
                    "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                    "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                    "STARGATE_CLAIM_REWARDS",
                )
            )
            .businessEventContracts(
                listOf(stargateNftContract, stargateDelegationContract, VTHO_CONTRACT)
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
