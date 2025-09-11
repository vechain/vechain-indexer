package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("validator")
open class ValidatorConfig {
    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        @Value("\${indexer.startBlock.validator}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.vevote}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.vevote}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}") stargateNFTAddress: String,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_STAKER_CONTRACT}")
        stargateStakerAddress: String,
    ): Indexer =
        IndexerFactory()
            .name("ValidatorIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .channelBatchSize(1)
            .includeFullBlock()
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/stargate")
            .abiContracts(listOf(stargateNFTAddress, builtinStakerAddress, stargateStakerAddress))
            .abiEventNames(listOf("DelegationInitiated", "DelegationAdded", "DelegationWithdrawn"))
            .excludeVetTransfers()
            .build()
}
