package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import org.springframework.stereotype.Component
import org.vechain.indexer.config.DefaultMetrics

@Component("indexerProcessorMetrics")
class ProcessorMetrics(private val registry: MeterRegistry) {

    private val processingDurationTimers = ConcurrentHashMap<String, Timer>()
    private val eventsCounters = ConcurrentHashMap<String, Counter>()

    fun observeProcessingDuration(indexerName: String, duration: Duration) {
        processingDurationTimers
            .computeIfAbsent(indexerName) {
                DefaultMetrics.newTimer("processor_duration")
                    .description("Duration of indexer processing")
                    .tag("indexer_name", indexerName)
                    .register(registry)
            }
            .record(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
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
}
