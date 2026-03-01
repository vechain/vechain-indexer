package org.vechain.indexer.config.metrics

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status
import org.vechain.indexer.config.HealthStatus
import org.vechain.indexer.config.IndexerHealthService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockUnexpanded

class IndexerMetricsReporterTest {

    private lateinit var metrics: IndexerHealthMetrics
    private lateinit var thorClient: ThorClient
    private lateinit var indexerHealthService: IndexerHealthService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        metrics = mockk(relaxed = true)
        thorClient = mockk()
        indexerHealthService = mockk()
    }

    private fun createBlockIndexer(name: String, blockNumber: Long): BlockIndexer {
        val indexer = mockk<BlockIndexer>()
        every { indexer.name } returns name
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.getCurrentBlockNumber() } returns blockNumber
        every { indexerHealthService.getIndexerHealth(indexer) } returns Pair(HealthStatus.UP, "ok")
        return indexer
    }

    private fun stubBestBlock(blockNumber: Long) {
        val block = mockk<BlockUnexpanded>()
        every { block.number } returns blockNumber
        coEvery { thorClient.getBlockUnexpanded(any()) } returns block
    }

    @Test
    fun `FULLY_SYNCED indexer sets estimated time to sync to zero`() {
        val indexer = createBlockIndexer("test-indexer", 1000L)
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("test-indexer", 0.0) }
    }

    @Test
    fun `first tick sets estimated time to sync to NaN when blocksPerSecond is unknown`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("test-indexer", match { it.isNaN() }) }
        verify(exactly = 0) { metrics.setEstimatedTimeToSync(any(), match { !it.isNaN() }) }
    }

    @Test
    fun `second tick with block progress computes estimated time to sync`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        every { indexer.getCurrentBlockNumber() } returns 200L
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("test-indexer", match { it > 0.0 }) }
    }

    @Test
    fun `no block progress keeps estimated time to sync as NaN`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Block number stays the same on second tick
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.setEstimatedTimeToSync(any(), match { !it.isNaN() }) }
    }

    @Test
    fun `INITIALISED BlockIndexer gets ETA of zero`() {
        val initialised = createBlockIndexer("initialised-indexer", 0L)
        every { initialised.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(initialised), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("initialised-indexer", 0.0) }
    }

    @Test
    fun `INITIALISED non-BlockIndexer gets ETA of zero`() {
        val initialised = mockk<Indexer>()
        every { initialised.name } returns "initialised-plain"
        every { initialised.getStatus() } returns Status.INITIALISED
        every { indexerHealthService.getIndexerHealth(initialised) } returns
            Pair(HealthStatus.UP, "ok")
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(initialised), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("initialised-plain", 0.0) }
    }

    @Test
    fun `incrementBlocksProcessed is called when block advances`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        every { indexer.getCurrentBlockNumber() } returns 200L
        reporter.reportMetrics()

        verify { metrics.incrementBlocksProcessed("test-indexer", 100.0) }
    }

    @Test
    fun `incrementBlocksProcessed is not called on first tick`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
    }

    @Test
    fun `incrementBlocksProcessed is not called when block does not advance`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
    }

    @Test
    fun `setBlocksPerSecond is called when block advances`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        every { indexer.getCurrentBlockNumber() } returns 200L
        reporter.reportMetrics()

        verify { metrics.setBlocksPerSecond("test-indexer", match { it > 0.0 }) }
    }

    @Test
    fun `setBlocksPerSecond is zero on first tick`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify { metrics.setBlocksPerSecond("test-indexer", 0.0) }
    }

    @Test
    fun `setBlocksPerSecond is zero when block does not advance`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()
        reporter.reportMetrics()

        // 3 calls: 1 from gauge init + 2 from report ticks
        verify(exactly = 3) { metrics.setBlocksPerSecond("test-indexer", 0.0) }
    }

    @Test
    fun `INITIALISED indexer reports zero blocks per second even when block number changes`() {
        val indexer = createBlockIndexer("test-indexer", 0L)
        every { indexer.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        every { indexer.getCurrentBlockNumber() } returns 400_000L
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
        // 3 calls: 1 from gauge init + 2 from report ticks
        verify(exactly = 3) { metrics.setBlocksPerSecond("test-indexer", 0.0) }
    }

    @Test
    fun `FULLY_SYNCED indexer reports blocks per second when block advances`() {
        val indexer = createBlockIndexer("test-indexer", 1000L)
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        stubBestBlock(1010L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        every { indexer.getCurrentBlockNumber() } returns 1005L
        reporter.reportMetrics()

        verify { metrics.incrementBlocksProcessed("test-indexer", 5.0) }
        verify { metrics.setBlocksPerSecond("test-indexer", match { it > 0.0 }) }
    }

    @Test
    fun `transition from INITIALISED to SYNCING does not spike blocks per second`() {
        val indexer = createBlockIndexer("test-indexer", 0L)
        every { indexer.getStatus() } returns Status.INITIALISED
        stubBestBlock(1_000_000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Indexer transitions to SYNCING and has caught up significantly
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.getCurrentBlockNumber() } returns 500_000L
        reporter.reportMetrics()

        // Should not spike: first processing tick has no previous processing-state data
        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
        // 3 calls: 1 from gauge init + 1 from INITIALISED tick + 1 from first SYNCING tick
        verify(exactly = 3) { metrics.setBlocksPerSecond("test-indexer", 0.0) }
    }

    @Test
    fun `transition from INITIALISED to SYNCING reports BPS on second syncing tick`() {
        val indexer = createBlockIndexer("test-indexer", 0L)
        every { indexer.getStatus() } returns Status.INITIALISED
        stubBestBlock(1_000_000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Indexer transitions to SYNCING
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.getCurrentBlockNumber() } returns 500_000L
        reporter.reportMetrics()

        // Block advances while still SYNCING — now BPS should be reported
        every { indexer.getCurrentBlockNumber() } returns 600_000L
        reporter.reportMetrics()

        verify { metrics.incrementBlocksProcessed("test-indexer", 100_000.0) }
        verify { metrics.setBlocksPerSecond("test-indexer", match { it > 0.0 }) }
    }

    @Test
    fun `multiple indexers track block deltas independently`() {
        val indexerA = createBlockIndexer("indexer-a", 100L)
        val indexerB = createBlockIndexer("indexer-b", 500L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(indexerA, indexerB),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()

        every { indexerA.getCurrentBlockNumber() } returns 150L
        every { indexerB.getCurrentBlockNumber() } returns 700L
        reporter.reportMetrics()

        verify { metrics.incrementBlocksProcessed("indexer-a", 50.0) }
        verify { metrics.incrementBlocksProcessed("indexer-b", 200.0) }
        verify { metrics.setBlocksPerSecond("indexer-a", match { it > 0.0 }) }
        verify { metrics.setBlocksPerSecond("indexer-b", match { it > 0.0 }) }
    }
}
