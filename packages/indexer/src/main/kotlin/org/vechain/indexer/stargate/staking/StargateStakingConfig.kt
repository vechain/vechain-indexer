package org.vechain.indexer.stargate.staking

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "stargate-staking")
open class StargateStakingConfig {
    @Bean
    open fun stargateStakingIndexer(
        thorClient: ThorClient,
        processor: StargateStakingProcessor,
        @Value("\${indexer.start-block.stargate-staking}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.STARGATE_STAKING.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_STAKE", "STARGATE_UNSTAKE"))
            .businessEventContracts(listOf(stargateNftContract, stargateDelegationContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
