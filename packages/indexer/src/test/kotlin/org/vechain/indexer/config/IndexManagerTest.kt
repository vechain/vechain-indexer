package org.vechain.indexer.config

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerRunner
import org.vechain.indexer.Status
import org.vechain.indexer.config.metrics.IndexerHealthMetrics
import org.vechain.indexer.thor.client.ThorClient

class IndexManagerTest {

    private lateinit var appCoroutineScope: CoroutineScope
    private lateinit var metrics: IndexerHealthMetrics
    private lateinit var applicationContext: ApplicationContext
    private lateinit var thorClient: ThorClient

    @BeforeEach
    fun setup() {
        clearAllMocks()
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        metrics = mockk(relaxed = true)
        applicationContext = mockk()
        thorClient = mockk()
    }

    @AfterEach
    fun tearDown() {
        appCoroutineScope.coroutineContext.cancel()
        unmockkObject(IndexerRunner.Companion)
    }

    @Test
    fun `onShutdown publishes final shutdown metrics for block indexers`() {
        val indexer = mockk<BlockIndexer>()
        var status = Status.FULLY_SYNCED
        every { indexer.name } returns "test-indexer"
        every { indexer.shutDown() } answers { status = Status.SHUT_DOWN }
        every { indexer.getStatus() } answers { status }
        every { indexer.getCurrentBlockNumber() } returns 1234L

        val manager =
            IndexManager(
                indexers = listOf(indexer),
                processors = emptyList(),
                indexBootstrapState = mockk(),
                appCoroutineScope = appCoroutineScope,
                thorClient = thorClient,
                metrics = metrics,
                applicationContext = applicationContext,
                channelBatchSize = 10,
                catchUpIntervalSeconds = 5,
            )

        manager.onShutdown()

        verify(exactly = 1) { indexer.shutDown() }
        verify { metrics.setComponentHealth("test-indexer", "indexer", 0.0) }
        verify { metrics.setIndexerSyncStatus("test-indexer", Status.SHUT_DOWN) }
        verify { metrics.setIndexerCurrentBlock("test-indexer", 1234L) }
    }

    @Test
    fun `onShutdown flushes each processor's checkpoint`() {
        val processorA = mockk<BaseProcessor>()
        val processorB = mockk<BaseProcessor>()
        every { processorA.flushCheckpoint() } just Runs
        every { processorB.flushCheckpoint() } just Runs

        val manager =
            IndexManager(
                indexers = emptyList(),
                processors = listOf(processorA, processorB),
                indexBootstrapState = mockk(),
                appCoroutineScope = appCoroutineScope,
                thorClient = thorClient,
                metrics = metrics,
                applicationContext = applicationContext,
                channelBatchSize = 10,
                catchUpIntervalSeconds = 5,
            )

        manager.onShutdown()

        verify(exactly = 1) { processorA.flushCheckpoint() }
        verify(exactly = 1) { processorB.flushCheckpoint() }
    }

    @Test
    fun `onShutdown continues flushing and publishing metrics when one flush throws`() {
        // The shutdown sequence must be best-effort: a single processor that fails to flush its
        // checkpoint cannot starve later processors or the final-metrics publish step. Each step
        // is independently try/catched in IndexManager.onShutdown.
        val processorA = mockk<BaseProcessor>()
        val processorB = mockk<BaseProcessor>()
        every { processorA.flushCheckpoint() } throws RuntimeException("flush A failed")
        every { processorB.flushCheckpoint() } just Runs

        val indexer = mockk<BlockIndexer>()
        var status = Status.FULLY_SYNCED
        every { indexer.name } returns "test-indexer"
        every { indexer.shutDown() } answers { status = Status.SHUT_DOWN }
        every { indexer.getStatus() } answers { status }
        every { indexer.getCurrentBlockNumber() } returns 42L

        val manager =
            IndexManager(
                indexers = listOf(indexer),
                processors = listOf(processorA, processorB),
                indexBootstrapState = mockk(),
                appCoroutineScope = appCoroutineScope,
                thorClient = thorClient,
                metrics = metrics,
                applicationContext = applicationContext,
                channelBatchSize = 10,
                catchUpIntervalSeconds = 5,
            )

        manager.onShutdown()

        verify(exactly = 1) { processorA.flushCheckpoint() }
        verify(exactly = 1) { processorB.flushCheckpoint() }
        verify { metrics.setIndexerCurrentBlock("test-indexer", 42L) }
    }

    @Test
    fun `start marks the bootstrap ready and launches the runner`() {
        mockkObject(IndexerRunner.Companion)
        val runnerJob = mockk<Job>(relaxed = true)
        every {
            IndexerRunner.launch(any(), any(), any<List<Indexer>>(), any(), any(), any(), any())
        } returns runnerJob

        val indexBootstrapState = mockk<IndexBootstrapState>(relaxed = true)
        val indexer = mockk<Indexer>(relaxed = true)
        every { indexer.name } returns "test-indexer"

        val manager =
            IndexManager(
                indexers = listOf(indexer),
                processors = emptyList(),
                indexBootstrapState = indexBootstrapState,
                appCoroutineScope = appCoroutineScope,
                thorClient = thorClient,
                metrics = metrics,
                applicationContext = applicationContext,
                channelBatchSize = 10,
                catchUpIntervalSeconds = 5,
            )

        manager.start()

        verifyOrder {
            indexBootstrapState.markRunning()
            indexBootstrapState.markReady()
        }
        verify(exactly = 1) {
            IndexerRunner.launch(any(), any(), any<List<Indexer>>(), any(), any(), any(), any())
        }
    }
}
