package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.thor.client.ThorClient

/**
 * Spring configuration that provides the default [ThorClient] bean for the application.
 *
 * This configuration registers a caching-enabled [ThorClient] implementation that will be used
 * whenever no other [ThorClient] bean is defined in the application context.
 */
@Configuration
open class ThorClientConfig {
    /**
     * Creates the default [ThorClient] bean backed by a caching implementation.
     *
     * The [thorUrl] is injected from the `thor.url` application property and is used as the base
     * URL for all requests made by the client. The returned client adds caching support and
     * configures a default project identifier header for the indexer.
     *
     * This bean is only created if no other [ThorClient] bean is present.
     */
    @Bean
    @ConditionalOnMissingBean(ThorClient::class)
    open fun thorClient(@Value("\${thor.url}") thorUrl: String): ThorClient =
        CachingThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
