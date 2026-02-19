package org.vechain.indexer.validator

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
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("validator", "delegation")
open class DelegationConfig {
    @Bean
    open fun delegationArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Delegation> =
        ArchiveService(mongoTemplate, Delegation::class.java, recordLimit)

    @Bean
    open fun delegationPruner(
        delegationArchiveService: ArchiveService<Delegation>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ) = PrunerService(delegationArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun delegationIndexer(
        thorClient: ThorClient,
        processor: DelegationProcessor,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${indexer.channel-batch-size}") channelBatchSize: Int,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.DELEGATION.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress, stargateContract, stargateNftContract))
            .abiEventNames(
                listOf(
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "DelegationWithdrawn",
                    "ValidationSignaledExit",
                    "DelegationRewardsClaimed",
                    "Transfer",
                )
            )
            .callDataClauses(listOf(ValidatorDecoder.buildClauses(getAllValidatorsAddress)[0]))
            .excludeVetTransfers()
            .build()
}
