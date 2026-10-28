package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component
import org.vechain.indexer.backfill.BackfillState

@Component
class IndexerHealthMetrics(private val registry: MeterRegistry) {

    private val componentHealthGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val backfillPhaseGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val currentBackfillPhase = ConcurrentHashMap<String, BackfillState.Phase>()
    private val deferrableIndexGauges = ConcurrentHashMap<String, AtomicReference<Double>>()
    private val bestBlockGauge = AtomicReference(0.0)
    private var bestBlockGaugeInitialized = false

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

    /** One-hot per schema; only the reporter thread writes it. */
    fun setBackfillPhase(schema: String, indexer: String, phase: BackfillState.Phase) {
        if (currentBackfillPhase.put(schema, phase) == phase) return
        BackfillState.Phase.entries.forEach {
            backfillPhaseGauge(schema, indexer, it).set(if (it == phase) 1.0 else 0.0)
        }
    }

    private fun backfillPhaseGauge(
        schema: String,
        indexer: String,
        phase: BackfillState.Phase,
    ): AtomicReference<Double> =
        backfillPhaseGauges.computeIfAbsent("$schema:${phase.name}") {
            val ref = AtomicReference(0.0)
            registry.gauge(
                "indexer_backfill_phase",
                tags(schema, indexer) + Tag.of("phase", phase.name),
                ref,
            ) {
                it.get()
            }
            ref
        }

    /** Standing against declared: how far a rebuild has got, and what a backfill is skipping. */
    fun setDeferrableIndexes(schema: String, indexer: String, standing: Int, declared: Int) {
        indexGauge("indexer_deferrable_indexes_standing", schema, indexer).set(standing.toDouble())
        indexGauge("indexer_deferrable_indexes_declared", schema, indexer).set(declared.toDouble())
    }

    private fun indexGauge(
        name: String,
        schema: String,
        indexer: String,
    ): AtomicReference<Double> =
        deferrableIndexGauges.computeIfAbsent("$name:$schema") {
            val ref = AtomicReference(0.0)
            registry.gauge(name, tags(schema, indexer), ref) { it.get() }
            ref
        }

    private fun tags(schema: String, indexer: String) =
        listOf(Tag.of("schema", schema), Tag.of("indexer", indexer))

    fun setBestBlockNumber(blockNumber: Long) {
        if (!bestBlockGaugeInitialized) {
            registry.gauge("thor_best_block_number", bestBlockGauge) { it.get() }
            bestBlockGaugeInitialized = true
        }
        bestBlockGauge.set(blockNumber.toDouble())
    }
}
