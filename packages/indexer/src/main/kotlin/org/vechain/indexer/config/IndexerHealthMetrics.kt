package org.vechain.indexer.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.Locale
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
        setIndexerSyncStatusCode(indexerName, syncStatus)

        Status.entries.forEach { status ->
            val key = "$indexerName:${status.name}"
            val statusReadable = status.name.toReadableEnumLabel()
            syncStatusGauges
                .computeIfAbsent(key) {
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
                .set(if (status == syncStatus) 1.0 else 0.0)
        }
    }

    fun setIndexerCurrentBlock(indexerName: String, blockNumber: Long) {
        currentBlockGauges
            .computeIfAbsent(indexerName) { name ->
                val ref = AtomicReference(0.0)
                registry.gauge(
                    "indexer_current_block_gauge",
                    listOf(Tag.of("indexer_name", name)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(blockNumber.toDouble())
    }

    private fun setIndexerSyncStatusCode(indexerName: String, syncStatus: Status) {
        syncStatusCodeGauges
            .computeIfAbsent(indexerName) { name ->
                val ref = AtomicReference(-1.0)
                registry.gauge(
                    "indexer_sync_status_code_gauge",
                    listOf(Tag.of("indexer_name", name)),
                    ref,
                ) {
                    it.get()
                }
                ref
            }
            .set(syncStatus.toStatusCode())
    }

    private fun Status.toStatusCode(): Double =
        when (name) {
            "NOT_INITIALISED" -> 0.0
            "INITIALISED" -> 1.0
            "SYNCING" -> 2.0
            "FAST_SYNCING" -> 3.0
            "PRUNING" -> 4.0
            "SHUT_DOWN" -> 5.0
            else -> -1.0
        }

    private fun String.toReadableEnumLabel(): String =
        split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
}
