package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.config.metrics.ThorClientMetrics
import org.vechain.indexer.thor.client.ThorClient

@Configuration
open class IndexerConfig() {
    @Bean
    open fun thorClient(
        @Value("\${thor.url}") thorUrl: String,
        metrics: ThorClientMetrics,
    ): ThorClient = MonitoredThorClient(metrics, thorUrl, Pair("X-Project-Id", "veworld-indexer"))
}
