package org.vechain.indexer.config

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Status
import org.vechain.indexer.config.metrics.IndexerHealthMetrics
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionCollectionConfig

class IndexManagerTest {

    private lateinit var appCoroutineScope: CoroutineScope
    private lateinit var metrics: IndexerHealthMetrics
    private lateinit var applicationContext: ApplicationContext
    private lateinit var thorClient: ThorClient
    private lateinit var indexerVersionCollectionConfig: IndexerVersionCollectionConfig

    @BeforeEach
    fun setup() {
        clearAllMocks()
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        metrics = mockk(relaxed = true)
        applicationContext = mockk()
        thorClient = mockk()
        indexerVersionCollectionConfig = mockk()
    }

    @AfterEach
    fun tearDown() {
        appCoroutineScope.coroutineContext.cancel()
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
                collectionConfigs = emptyList(),
                indexerVersionCollectionConfig = indexerVersionCollectionConfig,
                indexBootstrapState = mockk(),
                appCoroutineScope = appCoroutineScope,
                thorClient = thorClient,
                metrics = metrics,
                applicationContext = applicationContext,
                channelBatchSize = 10,
            )

        manager.onShutdown()

        verify(exactly = 1) { indexer.shutDown() }
        verify { metrics.setComponentHealth("test-indexer", "indexer", 0.0) }
        verify { metrics.setIndexerSyncStatus("test-indexer", Status.SHUT_DOWN) }
        verify { metrics.setIndexerCurrentBlock("test-indexer", 1234L) }
        verify { metrics.setBlocksPerSecond("test-indexer", 0.0) }
    }
}
