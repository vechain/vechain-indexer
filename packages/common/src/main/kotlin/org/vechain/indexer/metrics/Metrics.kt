package org.vechain.indexer.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

@Component
class Metrics(private val registry: MeterRegistry) {

    private val responseCodeCounters = ConcurrentHashMap<String, Counter>()
    private val requestDurationTimers = ConcurrentHashMap<String, Timer>()
    private val bestBlockGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val processingDurationTimers = ConcurrentHashMap<String, Timer>()
    private val eventsCounters = ConcurrentHashMap<String, Counter>()
    private val componentHealthGauges = ConcurrentHashMap<String, AtomicReference<Double>>()

    @PostConstruct
    fun init() {
        instance = this
    }

    // Thor client metrics
    fun recordResponseCode(method: String, path: String, code: String) {
        val endpoint = endpointName(method, path)
        val key = "$endpoint:$code"
        responseCodeCounters
            .computeIfAbsent(key) {
                Counter.builder("thor_client_response_codes_total")
                    .description("Count of response codes from Thor client")
                    .tag("endpoint", endpoint)
                    .tag("code", code)
                    .register(registry)
            }
            .increment()
    }

    fun observeRequestDuration(method: String, path: String, durationMs: Double) {
        val endpoint = endpointName(method, path)
        requestDurationTimers
            .computeIfAbsent(endpoint) {
                Timer.builder("thor_client_request_duration")
                    .description("Duration of Thor client requests")
                    .tag("endpoint", endpoint)
                    .publishPercentileHistogram()
                    .register(registry)
            }
            .record(durationMs.toLong(), TimeUnit.MILLISECONDS)
    }

    // Indexer metrics
    fun setBestBlock(indexerName: String, blockNumber: Double) {
        bestBlockGauges
            .computeIfAbsent(indexerName) { name ->
                val ref = AtomicReference(0.0)
                registry.gauge("best_block_gauge", listOf(Tag.of("indexer_name", name)), ref) {
                    it.get()
                }
                ref
            }
            .set(blockNumber)
    }

    fun observeProcessingDuration(indexerName: String, durationMs: Double) {
        processingDurationTimers
            .computeIfAbsent(indexerName) {
                Timer.builder("processor_duration")
                    .description("Duration of indexer processing")
                    .tag("indexer_name", indexerName)
                    .publishPercentileHistogram()
                    .register(registry)
            }
            .record(durationMs.toLong(), TimeUnit.MILLISECONDS)
    }

    fun incrementEventsCounter(indexerName: String, count: Double) {
        eventsCounters
            .computeIfAbsent(indexerName) {
                Counter.builder("processor_events_counter")
                    .description("Count of events processed")
                    .tag("indexer_name", indexerName)
                    .register(registry)
            }
            .increment(count)
    }

    // Health metrics
    fun setComponentHealth(name: String, type: String, value: Double) {
        val key = "$name:$type"
        componentHealthGauges
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

    private fun endpointName(method: String, path: String): String {
        if (method == HttpMethod.POST.toString()) {
            return "POST $path"
        }
        var cleanPath = path
        cleanPath = hexRegex.replace(cleanPath, "{hex}")
        cleanPath = numberRegex.replace(cleanPath, "{number}")
        return "$method $cleanPath"
    }

    companion object {
        private val numberRegex = Regex("""[0-9]+""")
        private val hexRegex = Regex("""0x[0-9a-fA-F]+""")

        @Volatile private lateinit var instance: Metrics

        // Static accessors for non-Spring classes
        fun recordResponseCode(method: String, path: String, code: String) =
            instance.recordResponseCode(method, path, code)

        fun observeRequestDuration(method: String, path: String, durationMs: Double) =
            instance.observeRequestDuration(method, path, durationMs)

        fun setBestBlock(indexerName: String, blockNumber: Double) =
            instance.setBestBlock(indexerName, blockNumber)

        fun observeProcessingDuration(indexerName: String, durationMs: Double) =
            instance.observeProcessingDuration(indexerName, durationMs)

        fun incrementEventsCounter(indexerName: String, count: Double) =
            instance.incrementEventsCounter(indexerName, count)

        fun setComponentHealth(name: String, type: String, value: Double) =
            instance.setComponentHealth(name, type, value)
    }
}
