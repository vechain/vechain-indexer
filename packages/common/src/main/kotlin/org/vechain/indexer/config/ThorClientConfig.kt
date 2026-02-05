package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.thor.client.ThorClient

@Configuration
open class ThorClientConfig {
    @Bean
    @ConditionalOnMissingBean(ThorClient::class)
    open fun thorClient(@Value("\${thor.url}") thorUrl: String): ThorClient =
        CachingThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
