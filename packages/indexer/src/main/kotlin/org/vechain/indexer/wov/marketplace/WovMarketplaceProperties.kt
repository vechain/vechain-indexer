package org.vechain.indexer.wov.marketplace

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** The marketplace contracts, one per sale mechanism; mainnet only. */
@Profile("wov-marketplace")
@Configuration
@ConfigurationProperties(prefix = "indexer.wov-marketplace")
open class WovMarketplaceProperties {
    var contracts: List<String> = emptyList()
}
