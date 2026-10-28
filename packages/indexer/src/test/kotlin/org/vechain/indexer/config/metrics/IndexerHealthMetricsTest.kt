package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.backfill.BackfillState

class IndexerHealthMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: IndexerHealthMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = IndexerHealthMetrics(registry)
    }

    @Test
    fun `setBestBlockNumber creates gauge with no tags and sets value`() {
        metrics.setBestBlockNumber(12345L)

        val gauge = registry.find("thor_best_block_number").gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(12345.0)
    }

    // --- component_health_status gauge tests ---

    @Test
    fun `setComponentHealth creates gauge tagged by name and type`() {
        metrics.setComponentHealth("test-indexer", "indexer", 1.0)

        val gauge =
            registry
                .find("component_health_status")
                .tag("name", "test-indexer")
                .tag("type", "indexer")
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(1.0)
    }

    @Test
    fun `setDeferrableIndexes gauges standing against declared per schema`() {
        metrics.setDeferrableIndexes("history", "HistoryIndexer", standing = 3, declared = 14)
        metrics.setDeferrableIndexes("history", "HistoryIndexer", standing = 4, declared = 14)

        assertThat(gauge("indexer_deferrable_indexes_standing", "history")).isEqualTo(4.0)
        assertThat(gauge("indexer_deferrable_indexes_declared", "history")).isEqualTo(14.0)
        assertThat(
                registry.find("indexer_deferrable_indexes_standing").tag("schema", "nft").gauge()
            )
            .isNull()
    }

    @Test
    fun `the gauges carry the indexer that owns the schema, not only the schema`() {
        metrics.setDeferrableIndexes("history", "HistoryIndexer", standing = 1, declared = 2)
        metrics.setBackfillPhase("history", "HistoryIndexer", BackfillState.Phase.BACKFILL)

        assertThat(tagged("indexer_deferrable_indexes_standing")).isEqualTo(1.0)
        assertThat(tagged("indexer_backfill_phase", "BACKFILL")).isEqualTo(1.0)
        assertThat(tagged("indexer_backfill_phase", "SERVING")).isEqualTo(0.0)
    }

    private fun tagged(name: String, phase: String? = null) =
        registry
            .find(name)
            .tag("indexer", "HistoryIndexer")
            .let { if (phase == null) it else it.tag("phase", phase) }
            .gauge()
            ?.value()

    private fun gauge(name: String, schema: String) =
        registry.find(name).tag("schema", schema).gauge()?.value()
}
