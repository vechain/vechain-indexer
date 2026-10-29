package org.vechain.indexer.stargate.marketplace

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
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
import org.vechain.indexer.thor.Address

class StargateMarketplaceProcessorTest {
    private val service = mockk<StargateMarketplaceService>()
    private val processor =
        StargateMarketplaceProcessor(
            service,
            mockk<StargateMarketplaceWriteRepository>(relaxed = true),
            mockk<IndexerStateRepository>(relaxed = true),
            CheckpointProperties(),
            InlineVersioningProperties(),
            ProcessorMetrics(SimpleMeterRegistry()),
        )

    @Test
    fun `an entry with state changes saves them`() {
        val event = event(900)
        val row =
            StargateListing(
                saleId = BigInteger.ONE,
                blockNumber = 900,
                blockId = "0x01",
                blockTimestamp = 9_000,
                txId = "0xtx",
                tokenId = BigInteger.TWO,
                seller = "0x" + "aa".repeat(20),
                price = BigInteger.TEN,
                paymentToken = Address.ZERO_ADDRESS,
                status = ListingStatus.OPEN,
            )
        every { service.process(listOf(event)) } returns listOf(row)
        every { service.save(any()) } returns Unit

        runBlocking {
            processor.process(IndexingResult.LogResult(900, listOf(event), Status.SYNCING))
        }

        verify(exactly = 1) { service.save(listOf(row)) }
    }

    @Test
    fun `an entry without state changes saves nothing`() {
        every { service.process(emptyList()) } returns emptyList()

        runBlocking {
            processor.process(IndexingResult.LogResult(900, emptyList(), Status.SYNCING))
        }

        verify(exactly = 0) { service.save(any()) }
    }

    private fun event(block: Long) =
        IndexedEvent(
            id = "e",
            blockId = "0x01",
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0xtx",
            origin = null,
            params = AbiEventParameters(emptyMap(), StargateMarketplaceService.LISTING),
            eventType = StargateMarketplaceService.LISTING,
            clauseIndex = 0,
        )
}
