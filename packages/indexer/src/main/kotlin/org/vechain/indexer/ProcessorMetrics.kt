package org.vechain.indexer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component

@Component("indexerProcessorMetrics")
class ProcessorMetrics(private val registry: MeterRegistry) {

    private val bestBlockGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val processingDurationTimers = ConcurrentHashMap<String, Timer>()
    private val eventsCounters = ConcurrentHashMap<String, Counter>()

    @PostConstruct
    fun init() {
        setInstance(this)
    }

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

    companion object {
        @Volatile private var instance: ProcessorMetrics? = null

        private fun setInstance(metrics: ProcessorMetrics) {
            instance = metrics
        }

        fun setBestBlock(indexerName: String, blockNumber: Double) {
            instance?.setBestBlock(indexerName, blockNumber)
        }

        fun observeProcessingDuration(indexerName: String, durationMs: Double) {
            instance?.observeProcessingDuration(indexerName, durationMs)
        }

        fun incrementEventsCounter(indexerName: String, count: Double) {
            instance?.incrementEventsCounter(indexerName, count)
        }
    }
}
