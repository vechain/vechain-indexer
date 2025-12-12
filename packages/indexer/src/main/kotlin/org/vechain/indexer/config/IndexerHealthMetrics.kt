package org.vechain.indexer.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component

@Component
class IndexerHealthMetrics(private val registry: MeterRegistry) {

    private val gauges = ConcurrentHashMap<String, AtomicReference<Double>>()

    fun setComponentHealth(name: String, type: String, value: Double) {
        val key = "$name:$type"
        gauges
            .computeIfAbsent(key) {
                val ref = AtomicReference(0.0)
                registry.gauge(
                    "component_health_status_gauge",
                    listOf(Tag.of("name", name), Tag.of("type", type)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(value)
    }
}
