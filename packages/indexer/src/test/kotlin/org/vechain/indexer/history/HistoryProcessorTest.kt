package org.vechain.indexer.history

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_SINGLE_CLAUSE
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import org.vechain.indexer.version.IndexerVersionService
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class HistoryProcessorTest {
    @MockK lateinit var historyRepository: HistoryRepository

    @MockK lateinit var historyService: HistoryService

    @MockK lateinit var indexerVersionService: IndexerVersionService

    private lateinit var processor: HistoryProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        processor =
            HistoryProcessor(
                repository = historyRepository,
                historyService = historyService,
                indexerVersionService = indexerVersionService,
            )
    }

    @Test
    fun `process - if no events or transaction ar present then historyService shouldn't be called`() {
        processor.process(
            IndexingResult.Normal(BlockFixtures.BLOCK_NO_CLAUSES, emptyList(), emptyList())
        )

        verify { historyService wasNot Called }
    }

    @Test
    fun `processEvents - if events are present but no transactions then processBlockEvents should be called`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_NO_CLAUSES

        every { historyService.processEvents(events, block) } returns emptyList()

        processor.process(IndexingResult.Normal(block, events, emptyList()))

        verify(exactly = 1) { historyService.processEvents(events, BlockFixtures.BLOCK_NO_CLAUSES) }
    }

    @Test
    fun `process - if transactions are present but no events then processBlockEvents should be called`() {
        val block = BLOCK_SINGLE_CLAUSE
        val events = emptyList<IndexedEvent>()

        every { historyService.processEvents(events, block) } returns emptyList()

        processor.process(IndexingResult.Normal(block, events, emptyList()))

        verify(exactly = 1) { historyService.processEvents(events, block) }
    }

    @Test
    fun `process - if block is null then an exception should be thrown`() {
        val events = INDEXED_EVENTS_BLACKLIST

        try {
            processor.process(
                IndexingResult.EventsOnly(events.maxBy { it.blockNumber }.blockNumber, events)
            )
        } catch (e: IllegalArgumentException) {
            expect { that(e.message).isEqualTo("Block cannot be null") }
        }

        verify { historyService wasNot Called }
    }

    @Test
    fun `process - if processEvents returns records then save should be called`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_NO_CLAUSES
        val records = listOf<IndexedHistoryEvent>(mockk<IndexedHistoryEvent>())

        every { historyService.processEvents(events, block) } returns records
        every { historyService.save(records) } returns Unit

        processor.process(IndexingResult.Normal(block, events, emptyList()))

        verify(exactly = 1) { historyService.processEvents(events, block) }
        verify(exactly = 1) { historyService.save(records) }
    }
}
