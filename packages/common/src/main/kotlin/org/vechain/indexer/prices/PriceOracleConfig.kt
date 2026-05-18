package org.vechain.indexer.prices

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.config.CachingThorClient
import org.vechain.indexer.thor.client.ThorClient

/**
 * Spring configuration for the price-oracle Thor client.
 *
 * The vechain.energy `PriceFeedOracle` is published on both mainnet and testnet (different contract
 * addresses), and the USD prices it returns describe the assets themselves rather than the network
 * being observed. Each deployment therefore configures the Thor REST base URL and contract address
 * for its target oracle explicitly in terraform: mainnet deployments point at the mainnet oracle,
 * testnet at the testnet oracle. Solo / devnet / custom networks have no oracle deployed and should
 * point at the mainnet one as a fallback.
 *
 * Defaults in `application.yaml` are mainnet so local dev "just works" without env vars; the Thor
 * REST base URL and contract address are env-var overridable.
 */
@Profile("prices")
@Configuration
open class PriceOracleConfig {

    @Bean
    open fun priceOracleThorClient(
        @Value("\${pricing.oracle.thor-url}") thorUrl: String
    ): ThorClient = CachingThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
