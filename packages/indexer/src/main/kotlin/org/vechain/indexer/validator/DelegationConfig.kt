package org.vechain.indexer.validator

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
@Profile("delegation")
open class DelegationConfig {
    @Bean
    open fun delegationArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Delegation, DelegationArchive> =
        ArchiveService(
            mongoTemplate,
            Delegation::class.java,
            DelegationArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun delegationPruner(
        delegationArchiveService: ArchiveService<Delegation, DelegationArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(DelegationArchive::class, delegationArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun delegationIndexer(
        thorClient: ThorClient,
        processor: DelegationProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.validator}") syncLogInterval: Long,
        @Value("\${indexer.channel-batch-size}") channelBatchSize: Int,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_STAKER_CONTRACT}")
        stargateStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}") stargateAddress: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): Indexer =
        IndexerFactory()
            .name("DelegationIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .channelBatchSize(channelBatchSize)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress, stargateStakerAddress, stargateAddress))
            .abiEventNames(
                listOf(
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "DelegationWithdrawn",
                    "ValidationSignaledExit",
                    "DelegationRewardsClaimed",
                )
            )
            .callDataClauses(listOf(ValidatorUtils.buildClauses(getAllValidatorsAddress)[0]))
            .excludeVetTransfers()
            .build()
}
