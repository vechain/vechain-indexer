package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.Status

class IndexerHealthMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: IndexerHealthMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = IndexerHealthMetrics(registry)
    }

    @Test
    fun `incrementBlocksProcessed creates counter and increments`() {
        metrics.incrementBlocksProcessed("test-indexer", 10.0)

        val counter =
            registry
                .find("indexer_blocks_processed_total")
                .tag("indexer_name", "test-indexer")
                .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(10.0)
    }

    @Test
    fun `incrementBlocksProcessed accumulates across multiple calls`() {
        metrics.incrementBlocksProcessed("test-indexer", 5.0)
        metrics.incrementBlocksProcessed("test-indexer", 3.0)
        metrics.incrementBlocksProcessed("test-indexer", 2.0)

        val counter =
            registry
                .find("indexer_blocks_processed_total")
                .tag("indexer_name", "test-indexer")
                .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(10.0)
    }

    @Test
    fun `incrementBlocksProcessed creates separate counters per indexer`() {
        metrics.incrementBlocksProcessed("indexer-a", 5.0)
        metrics.incrementBlocksProcessed("indexer-b", 3.0)

        val counterA =
            registry
                .find("indexer_blocks_processed_total")
                .tag("indexer_name", "indexer-a")
                .counter()
        val counterB =
            registry
                .find("indexer_blocks_processed_total")
                .tag("indexer_name", "indexer-b")
                .counter()

        assertThat(counterA!!.count()).isEqualTo(5.0)
        assertThat(counterB!!.count()).isEqualTo(3.0)
    }

    // --- indexer_sync_status one-hot gauge tests ---

    @Test
    fun `setIndexerSyncStatus creates sync status gauges for all statuses`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)

        Status.entries.forEach { status ->
            val gauge =
                registry
                    .find("indexer_sync_status")
                    .tag("indexer_name", "test-indexer")
                    .tag("status", status.name)
                    .gauge()

            assertThat(gauge)
                .describedAs("sync status gauge for ${status.name} should exist")
                .isNotNull
        }
    }

    @Test
    fun `setIndexerSyncStatus active status is 1 and inactive statuses are 0`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.FAST_SYNCING)

        Status.entries.forEach { status ->
            val gauge =
                registry
                    .find("indexer_sync_status")
                    .tag("indexer_name", "test-indexer")
                    .tag("status", status.name)
                    .gauge()

            val expectedValue = if (status == Status.FAST_SYNCING) 1.0 else 0.0
            assertThat(gauge!!.value())
                .describedAs("sync status gauge for ${status.name}")
                .isEqualTo(expectedValue)
        }
    }

    @Test
    fun `setIndexerSyncStatus switching status flips previous gauge to 0 and new gauge to 1`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)
        metrics.setIndexerSyncStatus("test-indexer", Status.FULLY_SYNCED)

        val syncingGauge =
            registry
                .find("indexer_sync_status")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        val fullySyncedGauge =
            registry
                .find("indexer_sync_status")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FULLY_SYNCED.name)
                .gauge()

        assertThat(syncingGauge!!.value()).isEqualTo(0.0)
        assertThat(fullySyncedGauge!!.value()).isEqualTo(1.0)
    }

    @Test
    fun `setIndexerSyncStatus never exposes two statuses at 1 during concurrent backward transitions`() {
        // Regression: backward transitions (later enum index → earlier index) used to expose a
        // window where a concurrent scrape observed both statuses at 1, because the new gauge was
        // raised at its enum index before the old gauge was cleared at a later index. Verifies
        // the clear-first-then-set-target ordering by racing a reader against toggling writers.
        val indexerName = "race-indexer"
        val duration = java.time.Duration.ofMillis(300)
        val stopAt = System.nanoTime() + duration.toNanos()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)

        val doubleOnes = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            val writer =
                executor.submit {
                    var toggle = false
                    while (System.nanoTime() < stopAt) {
                        // FULLY_SYNCED (index 5) → SYNCING (index 4) is a backward transition.
                        val next = if (toggle) Status.SYNCING else Status.FULLY_SYNCED
                        metrics.setIndexerSyncStatus(indexerName, next)
                        toggle = !toggle
                    }
                }
            val reader =
                executor.submit {
                    while (System.nanoTime() < stopAt) {
                        val onesNow =
                            Status.entries.count { status ->
                                val gauge =
                                    registry
                                        .find("indexer_sync_status")
                                        .tag("indexer_name", indexerName)
                                        .tag("status", status.name)
                                        .gauge()
                                gauge != null && gauge.value() == 1.0
                            }
                        if (onesNow > 1) doubleOnes.incrementAndGet()
                    }
                }
            writer.get()
            reader.get()
        } finally {
            executor.shutdownNow()
        }

        assertThat(doubleOnes.get())
            .describedAs("observed a scrape where more than one status gauge was 1")
            .isZero()
    }

    @Test
    fun `setIndexerSyncStatus does not emit status_readable tag`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.FAST_SYNCING)

        val gauge =
            registry
                .find("indexer_sync_status")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FAST_SYNCING.name)
                .gauge()

        assertThat(gauge!!.id.getTag("status_readable")).isNull()
    }

    // --- indexer_current_block gauge tests ---

    @Test
    fun `setIndexerCurrentBlock creates gauge with indexer_name tag`() {
        metrics.setIndexerCurrentBlock("test-indexer", 1000L)

        val gauge =
            registry.find("indexer_current_block").tag("indexer_name", "test-indexer").gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(1000.0)
    }

    @Test
    fun `setIndexerCurrentBlock updates block number in place`() {
        metrics.setIndexerCurrentBlock("test-indexer", 1000L)
        metrics.setIndexerCurrentBlock("test-indexer", 2000L)

        val gauges =
            registry.find("indexer_current_block").tag("indexer_name", "test-indexer").gauges()

        assertThat(gauges).hasSize(1)
        assertThat(gauges.first().value()).isEqualTo(2000.0)
    }

    // --- thor_best_block_number gauge tests ---

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
}
