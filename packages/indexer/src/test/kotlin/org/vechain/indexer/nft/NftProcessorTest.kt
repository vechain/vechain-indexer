package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT
import org.vechain.indexer.fixtures.NFTFixtures

internal class NftProcessorTest {

    private val service = mockk<NftService>(relaxed = true)
    private val processor =
        NftProcessor(
            service,
            mockk(relaxed = true),
            mockk(relaxed = true),
            CheckpointProperties(),
            InlineVersioningProperties(),
            mockk(relaxed = true),
        )

    private fun process(events: List<org.vechain.indexer.event.model.generic.IndexedEvent>) =
        runBlocking {
            processor.process(
                IndexingResult.LogResult(events.maxOf { it.blockNumber }, events, Status.SYNCING)
            )
        }

    @Test
    fun `an entry without events saves nothing`() {
        runBlocking { processor.process(IndexingResult.LogResult(5, emptyList(), Status.SYNCING)) }

        verify(exactly = 0) { service.processBlock(any()) }
        verify(exactly = 0) { service.save(any()) }
    }

    @Test
    fun `the projected states are saved`() {
        val projected = listOf(NFTFixtures.NFT_VIP181)
        every { service.processBlock(INDEXED_EVENTS_NFT_MINT) } returns projected

        process(INDEXED_EVENTS_NFT_MINT)

        verify(exactly = 1) { service.save(projected) }
    }
}
