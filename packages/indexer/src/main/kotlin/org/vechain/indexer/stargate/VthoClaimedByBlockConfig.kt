package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.contracts.Contants.VTHO_CONTRACT
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vtho-claimed-by-block")
open class VthoClaimedByBlockConfig {
    @Bean
    open fun vthoClaimedByBlockIndexer(
        thorClient: ThorClient,
        processor: VthoClaimedByBlockProcessor,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${indexer.sync-log-interval.stargate}") logInterval: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        @Value("\${business-event.substitutions.STARGATE_STAKER_CONTRACT}")
        stargateStakerAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("VthoClaimedByBlockIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .blockBatchSize(syncBlockBatchSize)
            .syncLoggerInterval(logInterval)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(
                listOf(
                    "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                    "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                    "STARGATE_CLAIM_REWARDS",
                )
            )
            .businessEventContracts(
                listOf(
                    stargateNftContract,
                    stargateDelegationContract,
                    stargateStakerAddress,
                    VTHO_CONTRACT,
                )
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
