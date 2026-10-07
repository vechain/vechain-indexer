package org.vechain.indexer.wov.marketplace

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded

class WovMarketplaceProcessorTest {
    private val service = mockk<WovMarketplaceService>()
    private val repository = mockk<WovMarketplaceWriteRepository>(relaxed = true)
    private val thorClient = mockk<ThorClient>()
    private val processor =
        WovMarketplaceProcessor(
            service,
            repository,
            thorClient,
            mockk<IndexerStateRepository>(relaxed = true),
            CheckpointProperties(),
            InlineVersioningProperties(),
            ProcessorMetrics(SimpleMeterRegistry()),
        )

    @Test
    fun `an entry ending on a block without events fetches that block for its timestamp`() {
        val block = mockk<BlockUnexpanded> { every { timestamp } returns 9_000L }
        coEvery { thorClient.getBlockUnexpanded(BlockRevision.Number(900)) } returns block
        every { service.process(emptyList(), 900, 9_000) } returns WovMarketplaceEntry(900, 9_000)
        every { service.save(any()) } returns Unit

        runBlocking {
            processor.process(IndexingResult.LogResult(900, emptyList(), Status.SYNCING))
        }

        verify(exactly = 1) { service.save(WovMarketplaceEntry(900, 9_000)) }
    }

    @Test
    fun `an entry ending on a block with an event takes the timestamp it already carries`() {
        val event = event(900, 9_001)
        every { service.process(listOf(event), 900, 9_001) } returns WovMarketplaceEntry(900, 9_001)
        every { service.save(any()) } returns Unit

        runBlocking {
            processor.process(IndexingResult.LogResult(900, listOf(event), Status.SYNCING))
        }

        coVerify(exactly = 0) { thorClient.getBlockUnexpanded(any()) }
        verify(exactly = 1) { service.save(WovMarketplaceEntry(900, 9_001)) }
    }

    private fun event(block: Long, timestamp: Long) =
        IndexedEvent(
            id = "e",
            blockId = "0x01",
            blockNumber = block,
            blockTimestamp = timestamp,
            txId = "0xtx",
            origin = null,
            params = AbiEventParameters(emptyMap(), "purchase"),
            eventType = "purchase",
            clauseIndex = 0,
        )
}
