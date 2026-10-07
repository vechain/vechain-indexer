package org.vechain.indexer.wov.marketplace

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
@Profile("wov-marketplace")
open class WovMarketplaceConfig {
    @Bean
    open fun wovMarketplaceIndexer(
        thorClient: ThorClient,
        processor: WovMarketplaceProcessor,
        properties: WovMarketplaceProperties,
        @Value("\${indexer.start-block.wov-marketplace}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.WOV_MARKETPLACE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/wov")
            .abiContracts(properties.contracts.map(HexUtils::normalise))
            .abiEventNames(WovMarketplaceService.EVENTS)
            .excludeVetTransfers()
            .build()
}
