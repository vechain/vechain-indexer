package org.vechain.indexer.stargate.token

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
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("stargate", "stargate-token")
open class StargateTokenConfig {
    @Bean
    open fun stargateTokenArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<StargateToken, StargateTokenArchive> =
        ArchiveService(
            mongoTemplate,
            StargateToken::class.java,
            StargateTokenArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun stargateTokenPruner(
        stargateTokenArchiveService: ArchiveService<StargateToken, StargateTokenArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<StargateToken, StargateTokenArchive> =
        PrunerService(
            StargateTokenArchive::class,
            stargateTokenArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun stargateIndexer(
        thorClient: ThorClient,
        processor: StargateTokenProcessor,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        stargateTokenPruner: TargetedPruner<StargateToken, StargateTokenArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.STARGATE_TOKEN)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(stargateTokenPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(stargateNftContract, stargateDelegationContract, stargateContract))
            .abiEventNames(
                listOf(
                    "TokenMinted",
                    "TokenBurned",
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "Transfer",
                    "TokenManagerAdded",
                    "TokenManagerRemoved",
                    "MaturityPeriodBoosted",
                    "ValidationSignaledExit",
                    "DelegationRewardsClaimed",
                    "BaseVTHORewardsClaimed",
                )
            )
            .callDataClauses(listOf(ValidatorDecoder.buildClauses(getAllValidatorsContract)[0]))
            .excludeVetTransfers()
            .build()
}
