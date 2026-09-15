package org.vechain.indexer.transfer

import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.postgres.IndexerStateRepository

@ExtendWith(MockKExtension::class)
class TransferProcessorTest {
    @MockK lateinit var service: TransferService

    @MockK lateinit var repository: TransferWriteRepository

    private val state: IndexerStateRepository = mockk(relaxed = true)

    private lateinit var processor: TransferProcessor

    private val event = buildIndexedEvent(blockNumber = 100, eventType = "Transfer")

    @BeforeEach
    fun setUp() {
        processor =
            TransferProcessor(
                service,
                repository,
                state,
                CheckpointProperties(),
                InlineVersioningProperties(),
                mockk(relaxed = true),
            )
    }

    private fun entry(events: List<org.vechain.indexer.event.model.generic.IndexedEvent>) =
        IndexingResult.LogResult(endBlock = 100, events = events, status = Status.SYNCING)

    @Test
    fun `an entry without events touches the service not at all`() {
        runBlocking { processor.process(entry(emptyList())) }

        verify { service wasNot Called }
    }

    @Test
    fun `an entry's transfers are processed and saved once`() {
        val update =
            TransferService.Update(
                transfers =
                    listOf(
                        IndexedTransferEvent(
                            id = "t",
                            blockId = "0x01",
                            blockNumber = 100,
                            blockTimestamp = 1000,
                            txId = "0x02",
                            from = "0x03",
                            to = "0x04",
                            value = "1",
                            tokenAddress = null,
                            tokenId = null,
                            topics = emptyList(),
                            eventType = TransferEventType.VET,
                        )
                    ),
                interactions = emptyList(),
            )
        every { service.processEvents(listOf(event)) } returns update
        every { service.save(update) } just Runs

        runBlocking { processor.process(entry(listOf(event))) }

        verify(exactly = 1) { service.save(update) }
    }

    @Test
    fun `an entry whose events are no transfers saves nothing`() {
        every { service.processEvents(listOf(event)) } returns
            TransferService.Update(emptyList(), emptyList())

        runBlocking { processor.process(entry(listOf(event))) }

        verify(exactly = 0) { service.save(any()) }
    }
}
