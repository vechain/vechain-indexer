package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IndexerHealthMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: IndexerHealthMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = IndexerHealthMetrics(registry)
    }

    @Test
    fun `incrementBlocksProcessed creates counter and increments by count`() {
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

        val counter =
            registry
                .find("indexer_blocks_processed_total")
                .tag("indexer_name", "test-indexer")
                .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(8.0)
    }

    @Test
    fun `setEstimatedTimeToSync creates gauge and sets value`() {
        metrics.setEstimatedTimeToSync("test-indexer", 120.5)

        val gauge =
            registry
                .find("indexer_estimated_time_to_sync_seconds")
                .tag("indexer_name", "test-indexer")
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(120.5)
    }

    @Test
    fun `setEstimatedTimeToSync updates value on subsequent calls`() {
        metrics.setEstimatedTimeToSync("test-indexer", 120.5)
        metrics.setEstimatedTimeToSync("test-indexer", 60.0)

        val gauge =
            registry
                .find("indexer_estimated_time_to_sync_seconds")
                .tag("indexer_name", "test-indexer")
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(60.0)
    }

    @Test
    fun `incrementBlocksProcessed creates separate counters per indexer`() {
        metrics.incrementBlocksProcessed("indexer-a", 10.0)
        metrics.incrementBlocksProcessed("indexer-b", 20.0)

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

        assertThat(counterA!!.count()).isEqualTo(10.0)
        assertThat(counterB!!.count()).isEqualTo(20.0)
    }
}
