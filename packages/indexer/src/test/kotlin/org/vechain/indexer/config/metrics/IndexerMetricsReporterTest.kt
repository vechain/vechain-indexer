package org.vechain.indexer.config.metrics

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.Indexer
import org.vechain.indexer.backfill.BackfillState
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.config.HealthStatus
import org.vechain.indexer.config.IndexerHealthService

class IndexerMetricsReporterTest {

    private lateinit var metrics: IndexerHealthMetrics
    private lateinit var chainHead: ChainHead
    private lateinit var indexerHealthService: IndexerHealthService
    private val backfillState = BackfillState()

    @BeforeEach
    fun setup() {
        clearAllMocks()
        metrics = mockk(relaxed = true)
        chainHead = mockk()
        indexerHealthService = mockk()
    }

    private fun indexer(name: String, health: HealthStatus): Indexer {
        val indexer = mockk<Indexer>()
        every { indexer.name } returns name
        every { indexerHealthService.getIndexerHealth(indexer) } returns Pair(health, "")
        return indexer
    }

    private fun reporter(vararg indexers: Indexer) =
        IndexerMetricsReporter(
            indexers.toList(),
            metrics,
            chainHead,
            indexerHealthService,
            backfillState,
        )

    @Test
    fun `publishes the best block when the chain head knows it`() {
        every { chainHead.bestBlockNumber() } returns 1_000L

        reporter().reportMetrics()

        verify { metrics.setBestBlockNumber(1_000L) }
    }

    @Test
    fun `skips the best block while the chain head has none`() {
        every { chainHead.bestBlockNumber() } returns null

        reporter().reportMetrics()

        verify(exactly = 0) { metrics.setBestBlockNumber(any()) }
    }

    @Test
    fun `maps each indexer's health onto the component health gauge`() {
        every { chainHead.bestBlockNumber() } returns null

        reporter(
                indexer("up", HealthStatus.UP),
                indexer("down", HealthStatus.DOWN),
                indexer("unknown", HealthStatus.UNKNOWN),
            )
            .reportMetrics()

        verify { metrics.setComponentHealth("up", "indexer", 1.0) }
        verify { metrics.setComponentHealth("down", "indexer", 0.0) }
        verify { metrics.setComponentHealth("unknown", "indexer", -1.0) }
    }
}
