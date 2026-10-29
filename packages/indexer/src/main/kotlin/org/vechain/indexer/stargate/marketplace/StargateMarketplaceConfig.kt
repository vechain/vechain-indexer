package org.vechain.indexer.stargate.marketplace

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate-marketplace")
open class StargateMarketplaceConfig {
    @Bean
    open fun stargateMarketplaceIndexer(
        thorClient: ThorClient,
        processor: StargateMarketplaceProcessor,
        @Value("\${indexer.start-block.stargate-marketplace}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.stargate-marketplace.contract-address}") contract: String,
    ): Indexer {
        require(contract.isNotBlank()) {
            "indexer.stargate-marketplace.contract-address is required"
        }
        return IndexerFactory()
            .name(IndexerNames.STARGATE_MARKETPLACE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/stargate-marketplace")
            .abiContracts(listOf(HexUtils.normalise(contract)))
            .abiEventNames(StargateMarketplaceService.EVENTS)
            .excludeVetTransfers()
            .build()
    }
}
