package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient

@Configuration
open class IndexerConfig(@Value("\${thor.url}") private val thorUrl: String) {
    @Bean
    open fun thorClient(): ThorClient =
        DefaultThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
