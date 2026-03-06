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
    private val syncStatusCodeGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val currentBlockGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val bestBlockGauge = AtomicReference(0.0)
    private var bestBlockGaugeInitialized = false
    private val syncGapGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val blocksProcessedCounters = ConcurrentHashMap<String, Counter>()
    private val blocksPerSecondGauges = ConcurrentHashMap<String, AtomicReference<Double>>()

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

    fun setIndexerSyncStatus(indexerName: String, syncStatus: Status) {
        // Code gauge: keyed by indexer_name only (no status_readable tag)
        getOrCreateSyncStatusCodeGauge(indexerName).set(syncStatus.toStatusCode())

        // Emit a stable one-hot gauge set so dashboards can sum current status safely.
        Status.entries.forEach { status ->
            val value = if (status == syncStatus) 1.0 else 0.0
            getOrCreateSyncStatusGauge(indexerName, status).set(value)
        }
    }

    fun setIndexerCurrentBlock(indexerName: String, blockNumber: Long) {
        getOrCreateCurrentBlockGauge(indexerName).set(blockNumber.toDouble())
    }

    private fun getOrCreateCurrentBlockGauge(indexerName: String): AtomicReference<Double> {
        return currentBlockGauges.computeIfAbsent(indexerName) { name ->
            val ref = AtomicReference(Double.NaN)
            registry.gauge(
                "indexer_current_block_gauge",
                listOf(Tag.of("indexer_name", name)),
                ref,
            ) {
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
            val statusReadable = status.name.toReadableEnumLabel()
            val ref = AtomicReference(0.0)
            registry.gauge(
                "indexer_sync_status_gauge",
                listOf(
                    Tag.of("indexer_name", indexerName),
                    Tag.of("status", status.name),
                    Tag.of("status_readable", statusReadable),
                ),
                ref,
            ) {
                it.get()
            }
            ref
        }
    }

    private fun getOrCreateSyncStatusCodeGauge(indexerName: String): AtomicReference<Double> {
        return syncStatusCodeGauges.computeIfAbsent(indexerName) { name ->
            val ref = AtomicReference(Double.NaN)
            registry.gauge(
                "indexer_sync_status_code_gauge",
                listOf(Tag.of("indexer_name", name)),
                ref,
            ) {
                it.get()
            }
            ref
        }
    }

    fun setBestBlockNumber(blockNumber: Long) {
        if (!bestBlockGaugeInitialized) {
            registry.gauge("thor_best_block_number_gauge", bestBlockGauge) { it.get() }
            bestBlockGaugeInitialized = true
        }
        bestBlockGauge.set(blockNumber.toDouble())
    }

    fun setIndexerSyncGap(indexerName: String, gap: Long) {
        syncGapGauges
            .computeIfAbsent(indexerName) { name ->
                val ref = AtomicReference(0.0)
                registry.gauge(
                    "indexer_sync_gap_gauge",
                    listOf(Tag.of("indexer_name", name)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(gap.toDouble())
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

    fun setBlocksPerSecond(indexerName: String, blocksPerSecond: Double) {
        blocksPerSecondGauges
            .computeIfAbsent(indexerName) { name ->
                val ref = AtomicReference(0.0)
                registry.gauge(
                    "indexer_blocks_per_second_gauge",
                    listOf(Tag.of("indexer_name", name)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(blocksPerSecond)
    }
}
