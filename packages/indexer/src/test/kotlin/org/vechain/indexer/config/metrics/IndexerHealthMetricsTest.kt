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

    @Test
    fun `setBlocksPerSecond creates gauge and sets value`() {
        metrics.setBlocksPerSecond("test-indexer", 15.5)

        val gauge =
            registry
                .find("indexer_blocks_per_second_gauge")
                .tag("indexer_name", "test-indexer")
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(15.5)
    }

    @Test
    fun `setBlocksPerSecond updates value on subsequent calls`() {
        metrics.setBlocksPerSecond("test-indexer", 15.5)
        metrics.setBlocksPerSecond("test-indexer", 20.0)

        val gauge =
            registry
                .find("indexer_blocks_per_second_gauge")
                .tag("indexer_name", "test-indexer")
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(20.0)
    }

    @Test
    fun `setBlocksPerSecond creates separate gauges per indexer`() {
        metrics.setBlocksPerSecond("indexer-a", 10.0)
        metrics.setBlocksPerSecond("indexer-b", 20.0)

        val gaugeA =
            registry
                .find("indexer_blocks_per_second_gauge")
                .tag("indexer_name", "indexer-a")
                .gauge()
        val gaugeB =
            registry
                .find("indexer_blocks_per_second_gauge")
                .tag("indexer_name", "indexer-b")
                .gauge()

        assertThat(gaugeA!!.value()).isEqualTo(10.0)
        assertThat(gaugeB!!.value()).isEqualTo(20.0)
    }

    // --- indexer_sync_status_code_gauge tests ---

    @Test
    fun `setIndexerSyncStatus creates status code gauge per status with correct tags`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)

        Status.entries.forEach { status ->
            val gauge =
                registry
                    .find("indexer_sync_status_code_gauge")
                    .tag("indexer_name", "test-indexer")
                    .tag("status", status.name)
                    .gauge()

            assertThat(gauge).describedAs("gauge for status ${status.name} should exist").isNotNull
            assertThat(gauge!!.id.getTag("status_readable"))
                .describedAs("status_readable tag should be present for ${status.name}")
                .isNotNull
        }
    }

    @Test
    fun `setIndexerSyncStatus active status reports expected code while others are NaN`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)

        val activeGauge =
            registry
                .find("indexer_sync_status_code_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        assertThat(activeGauge).isNotNull
        assertThat(activeGauge!!.value()).isEqualTo(2.0)

        Status.entries
            .filter { it != Status.SYNCING }
            .forEach { status ->
                val gauge =
                    registry
                        .find("indexer_sync_status_code_gauge")
                        .tag("indexer_name", "test-indexer")
                        .tag("status", status.name)
                        .gauge()

                assertThat(gauge).isNotNull
                assertThat(gauge!!.value())
                    .describedAs("inactive status ${status.name} should be NaN")
                    .isNaN()
            }
    }

    @Test
    fun `setIndexerSyncStatus switching status moves the code to the new status`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)
        metrics.setIndexerSyncStatus("test-indexer", Status.FULLY_SYNCED)

        val syncingGauge =
            registry
                .find("indexer_sync_status_code_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        val fullySyncedGauge =
            registry
                .find("indexer_sync_status_code_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FULLY_SYNCED.name)
                .gauge()

        assertThat(syncingGauge!!.value()).isNaN()
        assertThat(fullySyncedGauge!!.value()).isEqualTo(6.0)
    }

    @Test
    fun `setIndexerSyncStatus status code values match expected mapping`() {
        val expectedCodes =
            mapOf(
                Status.NOT_INITIALISED to 0.0,
                Status.INITIALISED to 1.0,
                Status.SYNCING to 2.0,
                Status.FAST_SYNCING to 3.0,
                Status.PRUNING to 4.0,
                Status.SHUT_DOWN to 5.0,
                Status.FULLY_SYNCED to 6.0,
            )

        expectedCodes.forEach { (status, expectedCode) ->
            val localRegistry = SimpleMeterRegistry()
            val localMetrics = IndexerHealthMetrics(localRegistry)
            localMetrics.setIndexerSyncStatus("test-indexer", status)

            val gauge =
                localRegistry
                    .find("indexer_sync_status_code_gauge")
                    .tag("indexer_name", "test-indexer")
                    .tag("status", status.name)
                    .gauge()

            assertThat(gauge!!.value())
                .describedAs("status code for ${status.name}")
                .isEqualTo(expectedCode)
        }
    }

    // --- indexer_sync_status_gauge tests ---

    @Test
    fun `setIndexerSyncStatus creates sync status gauge per status with correct tags`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.SYNCING)

        Status.entries.forEach { status ->
            val gauge =
                registry
                    .find("indexer_sync_status_gauge")
                    .tag("indexer_name", "test-indexer")
                    .tag("status", status.name)
                    .gauge()

            assertThat(gauge)
                .describedAs("sync status gauge for ${status.name} should exist")
                .isNotNull
            assertThat(gauge!!.id.getTag("status_readable")).isNotNull
        }
    }

    @Test
    fun `setIndexerSyncStatus active status is 1 while others are 0`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.FAST_SYNCING)

        val activeGauge =
            registry
                .find("indexer_sync_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FAST_SYNCING.name)
                .gauge()

        assertThat(activeGauge!!.value()).isEqualTo(1.0)

        Status.entries
            .filter { it != Status.FAST_SYNCING }
            .forEach { status ->
                val gauge =
                    registry
                        .find("indexer_sync_status_gauge")
                        .tag("indexer_name", "test-indexer")
                        .tag("status", status.name)
                        .gauge()

                assertThat(gauge!!.value())
                    .describedAs("inactive status ${status.name} should be 0")
                    .isEqualTo(0.0)
            }
    }

    // --- indexer_current_block_by_status_gauge tests ---

    @Test
    fun `setIndexerCurrentBlockByStatus creates gauge with correct tags`() {
        metrics.setIndexerCurrentBlockByStatus("test-indexer", 1000L, Status.SYNCING)

        val gauge =
            registry
                .find("indexer_current_block_by_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        assertThat(gauge).isNotNull
        assertThat(gauge!!.id.getTag("status_readable")).isNotNull
        assertThat(gauge.value()).isEqualTo(1000.0)
    }

    @Test
    fun `setIndexerCurrentBlockByStatus sets previous status to NaN on status change`() {
        metrics.setIndexerCurrentBlockByStatus("test-indexer", 1000L, Status.SYNCING)
        metrics.setIndexerCurrentBlockByStatus("test-indexer", 2000L, Status.FULLY_SYNCED)

        val syncingGauge =
            registry
                .find("indexer_current_block_by_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        val fullySyncedGauge =
            registry
                .find("indexer_current_block_by_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FULLY_SYNCED.name)
                .gauge()

        assertThat(syncingGauge!!.value()).isNaN()
        assertThat(fullySyncedGauge!!.value()).isEqualTo(2000.0)
    }

    @Test
    fun `setIndexerCurrentBlockByStatus updates block within same status`() {
        metrics.setIndexerCurrentBlockByStatus("test-indexer", 1000L, Status.SYNCING)
        metrics.setIndexerCurrentBlockByStatus("test-indexer", 1500L, Status.SYNCING)

        val gauge =
            registry
                .find("indexer_current_block_by_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.SYNCING.name)
                .gauge()

        assertThat(gauge!!.value()).isEqualTo(1500.0)
    }

    // --- status_readable tag format tests ---

    @Test
    fun `status_readable tag converts enum names to title case`() {
        metrics.setIndexerSyncStatus("test-indexer", Status.FAST_SYNCING)

        val gauge =
            registry
                .find("indexer_sync_status_gauge")
                .tag("indexer_name", "test-indexer")
                .tag("status", Status.FAST_SYNCING.name)
                .gauge()

        assertThat(gauge!!.id.getTag("status_readable")).isEqualTo("Fast Syncing")
    }

    // --- separate gauges per indexer tests ---

    @Test
    fun `setIndexerSyncStatus creates separate gauge sets per indexer`() {
        metrics.setIndexerSyncStatus("indexer-a", Status.SYNCING)
        metrics.setIndexerSyncStatus("indexer-b", Status.FULLY_SYNCED)

        val aGauge =
            registry
                .find("indexer_sync_status_code_gauge")
                .tag("indexer_name", "indexer-a")
                .tag("status", Status.SYNCING.name)
                .gauge()

        val bGauge =
            registry
                .find("indexer_sync_status_code_gauge")
                .tag("indexer_name", "indexer-b")
                .tag("status", Status.FULLY_SYNCED.name)
                .gauge()

        assertThat(aGauge!!.value()).isEqualTo(2.0)
        assertThat(bGauge!!.value()).isEqualTo(6.0)
    }
}
