package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component
import org.vechain.indexer.Status

@Component
class IndexerHealthMetrics(private val registry: MeterRegistry) {

    private val componentHealthGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val syncStatusGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val currentSyncStatus = ConcurrentHashMap<String, Status>()
    private val currentBlockGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val bestBlockGauge = AtomicReference(0.0)
    private var bestBlockGaugeInitialized = false
    private val blocksProcessedCounters = ConcurrentHashMap<String, Counter>()

    fun setComponentHealth(name: String, type: String, value: Double) {
        val key = "$name:$type"
        componentHealthGauges
            .computeIfAbsent(key) {
                val ref = AtomicReference(0.0)
                registry.gauge(
                    "component_health_status",
                    listOf(Tag.of("name", name), Tag.of("type", type)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(value)
    }

    // Emit a stable one-hot gauge set so dashboards can select the current status by tag.
    // Serialise clear-then-set per indexer via compute() so the @Scheduled reporter and the
    // ContextClosedEvent shutdown path (different threads) can't interleave and strand two
    // gauges at 1.
    fun setIndexerSyncStatus(indexerName: String, syncStatus: Status) {
        currentSyncStatus.compute(indexerName) { _, previous ->
            when {
                previous == null ->
                    Status.entries.forEach { getOrCreateSyncStatusGauge(indexerName, it) }
                previous != syncStatus -> getOrCreateSyncStatusGauge(indexerName, previous).set(0.0)
            }
            getOrCreateSyncStatusGauge(indexerName, syncStatus).set(1.0)
            syncStatus
        }
    }

    fun setIndexerCurrentBlock(indexerName: String, blockNumber: Long) {
        getOrCreateCurrentBlockGauge(indexerName).set(blockNumber.toDouble())
    }

    private fun getOrCreateCurrentBlockGauge(indexerName: String): AtomicReference<Double> {
        return currentBlockGauges.computeIfAbsent(indexerName) { name ->
            val ref = AtomicReference(Double.NaN)
            registry.gauge("indexer_current_block", listOf(Tag.of("indexer_name", name)), ref) {
                it.get()
            }
            ref
        }
    }

    private fun getOrCreateSyncStatusGauge(
        indexerName: String,
        status: Status,
    ): AtomicReference<Double> {
        val key = "$indexerName:${status.name}"
        return syncStatusGauges.computeIfAbsent(key) {
            val ref = AtomicReference(0.0)
            registry.gauge(
                "indexer_sync_status",
                listOf(Tag.of("indexer_name", indexerName), Tag.of("status", status.name)),
                ref,
            ) {
                it.get()
            }
            ref
        }
    }

    fun setBestBlockNumber(blockNumber: Long) {
        if (!bestBlockGaugeInitialized) {
            registry.gauge("thor_best_block_number", bestBlockGauge) { it.get() }
            bestBlockGaugeInitialized = true
        }
        bestBlockGauge.set(blockNumber.toDouble())
    }

    fun incrementBlocksProcessed(indexerName: String, count: Double) {
        val counter =
            blocksProcessedCounters[indexerName]
                ?: blocksProcessedCounters.computeIfAbsent(indexerName) { name ->
                    Counter.builder("indexer_blocks_processed_total")
                        .description("Total blocks processed by indexer")
                        .tag("indexer_name", name)
                        .register(registry)
                }
        counter.increment(count)
    }
}
