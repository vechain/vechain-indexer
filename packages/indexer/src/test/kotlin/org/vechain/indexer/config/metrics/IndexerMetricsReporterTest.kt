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
    fun `first tick does not increment blocks processed counter`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
    }

    @Test
    fun `second tick increments blocks processed by delta`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Advance block number
        every { indexer.getCurrentBlockNumber() } returns 150L
        reporter.reportMetrics()

        verify(exactly = 1) { metrics.incrementBlocksProcessed("test-indexer", 50.0) }
    }

    @Test
    fun `block number unchanged does not increment counter`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Block number stays the same
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
    }

    @Test
    fun `block number decreased (reorg) does not increment counter`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Block number goes backwards (reorg)
        every { indexer.getCurrentBlockNumber() } returns 90L
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
    }

    @Test
    fun `non-BlockIndexer does not trigger blocks processed counter`() {
        val indexer = mockk<Indexer>()
        every { indexer.name } returns "non-block-indexer"
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexerHealthService.getIndexerHealth(indexer) } returns Pair(HealthStatus.UP, "ok")
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.incrementBlocksProcessed(any(), any()) }
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
    fun `first tick does not set estimated time to sync`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.setEstimatedTimeToSync(any(), any()) }
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
    fun `no block progress does not update estimated time to sync`() {
        val indexer = createBlockIndexer("test-indexer", 100L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(listOf(indexer), metrics, thorClient, indexerHealthService)
        reporter.reportMetrics()

        // Block number stays the same on second tick
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.setEstimatedTimeToSync(any(), any()) }
    }

    @Test
    fun `INITIALISED indexer gets max ETA from syncing indexers`() {
        val syncingA = createBlockIndexer("syncing-a", 100L)
        val syncingB = createBlockIndexer("syncing-b", 500L)
        val initialised = createBlockIndexer("initialised-indexer", 0L)
        every { initialised.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(syncingA, syncingB, initialised),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()

        // Advance syncing indexers
        every { syncingA.getCurrentBlockNumber() } returns 200L
        every { syncingB.getCurrentBlockNumber() } returns 600L
        reporter.reportMetrics()

        // INITIALISED indexer should get the max ETA from syncing indexers
        verify { metrics.setEstimatedTimeToSync("initialised-indexer", match { it > 0.0 }) }
    }

    @Test
    fun `INITIALISED non-BlockIndexer gets max ETA from syncing indexers`() {
        val syncing = createBlockIndexer("syncing-indexer", 100L)
        val initialised = mockk<Indexer>()
        every { initialised.name } returns "initialised-plain"
        every { initialised.getStatus() } returns Status.INITIALISED
        every { indexerHealthService.getIndexerHealth(initialised) } returns
            Pair(HealthStatus.UP, "ok")
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(syncing, initialised),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()

        every { syncing.getCurrentBlockNumber() } returns 200L
        reporter.reportMetrics()

        verify { metrics.setEstimatedTimeToSync("initialised-plain", match { it > 0.0 }) }
    }

    @Test
    fun `INITIALISED indexer gets no ETA when no syncing indexers exist`() {
        val initialisedA = createBlockIndexer("init-a", 0L)
        every { initialisedA.getStatus() } returns Status.INITIALISED
        val initialisedB = createBlockIndexer("init-b", 0L)
        every { initialisedB.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(initialisedA, initialisedB),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()
        reporter.reportMetrics()

        verify(exactly = 0) { metrics.setEstimatedTimeToSync("init-a", any()) }
        verify(exactly = 0) { metrics.setEstimatedTimeToSync("init-b", any()) }
    }

    @Test
    fun `INITIALISED indexer gets no ETA when all others are FULLY_SYNCED`() {
        val fullySynced = createBlockIndexer("synced-indexer", 1000L)
        every { fullySynced.getStatus() } returns Status.FULLY_SYNCED
        val initialised = createBlockIndexer("initialised-indexer", 0L)
        every { initialised.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(fullySynced, initialised),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()
        reporter.reportMetrics()

        // Only SYNCING ETAs are used for INITIALISED; FULLY_SYNCED is excluded
        verify { metrics.setEstimatedTimeToSync("synced-indexer", 0.0) }
        verify(exactly = 0) { metrics.setEstimatedTimeToSync("initialised-indexer", any()) }
    }

    @Test
    fun `INITIALISED indexer gets no ETA when others are only FAST_SYNCING`() {
        val fastSyncing = createBlockIndexer("fast-syncing-indexer", 100L)
        every { fastSyncing.getStatus() } returns Status.FAST_SYNCING
        val initialised = createBlockIndexer("initialised-indexer", 0L)
        every { initialised.getStatus() } returns Status.INITIALISED
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(fastSyncing, initialised),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()

        every { fastSyncing.getCurrentBlockNumber() } returns 200L
        reporter.reportMetrics()

        // FAST_SYNCING ETAs are not used for INITIALISED estimates
        verify { metrics.setEstimatedTimeToSync("fast-syncing-indexer", match { it > 0.0 }) }
        verify(exactly = 0) { metrics.setEstimatedTimeToSync("initialised-indexer", any()) }
    }

    @Test
    fun `multiple indexers track deltas independently`() {
        val indexerA = createBlockIndexer("indexer-a", 100L)
        val indexerB = createBlockIndexer("indexer-b", 200L)
        stubBestBlock(1000L)

        val reporter =
            IndexerMetricsReporter(
                listOf(indexerA, indexerB),
                metrics,
                thorClient,
                indexerHealthService,
            )
        reporter.reportMetrics()

        every { indexerA.getCurrentBlockNumber() } returns 120L
        every { indexerB.getCurrentBlockNumber() } returns 250L
        reporter.reportMetrics()

        verify(exactly = 1) { metrics.incrementBlocksProcessed("indexer-a", 20.0) }
        verify(exactly = 1) { metrics.incrementBlocksProcessed("indexer-b", 50.0) }
    }
}
