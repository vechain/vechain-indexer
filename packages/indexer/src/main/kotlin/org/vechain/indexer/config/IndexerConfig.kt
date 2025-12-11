package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.thor.client.MonitoredThorClient
import org.vechain.indexer.thor.client.ThorClient

@Configuration
open class IndexerConfig() {
    @Bean
    open fun thorClient(@Value("\${thor.url}") thorUrl: String): ThorClient =
        MonitoredThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
