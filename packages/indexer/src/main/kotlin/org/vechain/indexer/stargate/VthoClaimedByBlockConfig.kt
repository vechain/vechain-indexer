package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
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
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer =
        IndexerFactory()
            .name("VthoClaimedByBlockIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(
                listOf("STARGATE_CLAIM_REWARDS_BASE", "STARGATE_CLAIM_REWARDS_DELEGATE")
            )
            .businessEventContracts(
                listOf(stargateNftContract, stargateDelegationContract, VTHO_CONTRACT)
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
