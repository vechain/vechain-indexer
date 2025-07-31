package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vet-staked-by-block")
open class VetStakedByBlockConfig {
    @Bean
    open fun vetStakedByBlockIndexer(
        thorClient: ThorClient,
        processor: VetStakedByBlockProcessor,
        @Value("\${indexer.startBlock.stargate}") startBlock: Long,
        @Value("\${indexer.syncBlockBatchSize.stargate}") syncBlockBatchSize: Long,
        @Value("\${indexer.syncLogInterval.stargate}") logInterval: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("VetStakedByBlockIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .blockBatchSize(syncBlockBatchSize)
            .syncLoggerInterval(logInterval)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_STAKE", "STARGATE_UNSTAKE"))
            .businessEventContracts(listOf(stargateNftContract, stargateDelegationContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
