package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

/**
 * Listens to the canonical `SafeProxyFactory` and records every Safe deployed on the network. This
 * is the trust root for the other Safe indexers — they treat an address as a real Safe iff it has a
 * document in the `safe_proxies` collection.
 */
@Configuration
@Profile("safe")
open class SafeProxyConfig {

    @Bean
    open fun safeProxyIndexer(
        thorClient: ThorClient,
        processor: SafeProxyProcessor,
        @Value("\${indexer.start-block.safe-proxies:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.SAFE_PROXY_FACTORY_CONTRACT}")
        safeProxyFactoryAddress: String,
    ): BlockIndexer {
        require(safeProxyFactoryAddress.isNotBlank()) {
            "SAFE_PROXY_FACTORY_CONTRACT must be configured when the 'safe' profile is active"
        }
        return IndexerFactory()
            .name(IndexerNames.SAFE_PROXY.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/safe-proxy-factory")
            .abiContracts(listOf(safeProxyFactoryAddress))
            .abiEventNames(listOf("ProxyCreation"))
            .build()
    }
}
