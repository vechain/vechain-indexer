package org.vechain.indexer.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class MetricsConfig(
    private val registry: MeterRegistry,
    @Value("\${metrics.id}") private val metricsId: String,
) {
    init {
        registry.config().commonTags("app-id", metricsId)
    }
}
