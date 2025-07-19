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
@Profile("stargate")
open class VthoClaimedByBlockConfig {
    @Bean
    open fun vthoClaimedByBlockIndexer(
        thorClient: ThorClient,
        processor: VthoClaimedByBlockProcessor,
        @Value("\${indexer.startBlock.stargate}") startBlock: Long,
        @Value("\${indexer.syncBlockBatchSize.stargate}") syncBlockBatchSize: Long,
        @Value("\${indexer.syncLogInterval.stargate}") logInterval: Long,
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
                listOf("STARGATE_CLAIM_REWARDS_BASE", "STARGATE_CLAIM_REWARDS_DELEGATE")
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
