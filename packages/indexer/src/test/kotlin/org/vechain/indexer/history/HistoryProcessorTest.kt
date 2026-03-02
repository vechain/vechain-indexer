package org.vechain.indexer.history

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_SINGLE_CLAUSE
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class HistoryProcessorTest {
    @MockK lateinit var historyRepository: HistoryRepository

    @MockK lateinit var historyService: HistoryService

    @MockK lateinit var checkpointService: CheckpointService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var processor: HistoryProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs

        processor =
            HistoryProcessor(
                repository = historyRepository,
                historyService = historyService,
                checkpointService = checkpointService,
                processorMetrics = processorMetrics,
            )
    }

    @Test
    fun `process - if no events or transaction ar present then historyService shouldn't be called`() {
        runBlocking {
            processor.process(
                IndexingResult.BlockResult(
                    BlockFixtures.BLOCK_NO_CLAUSES,
                    emptyList(),
                    emptyList(),
                    Status.FULLY_SYNCED,
                )
            )
        }

        verify { historyService wasNot Called }
    }

    @Test
    fun `processEvents - if events are present but no transactions then processBlockEvents should be called`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_NO_CLAUSES

        coEvery { historyService.processEvents(events, block) } returns emptyList()

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) {
            historyService.processEvents(events, BlockFixtures.BLOCK_NO_CLAUSES)
        }
    }

    @Test
    fun `process - if transactions are present but no events then processBlockEvents should be called`() {
        val block = BLOCK_SINGLE_CLAUSE
        val events = emptyList<IndexedEvent>()

        coEvery { historyService.processEvents(events, block) } returns emptyList()

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processEvents(events, block) }
    }

    @Test
    fun `process - if block is null then an exception should be thrown`() {
        val events = INDEXED_EVENTS_BLACKLIST

        try {
            runBlocking {
                processor.process(
                    IndexingResult.LogResult(
                        events.maxBy { it.blockNumber }.blockNumber,
                        events,
                        Status.SYNCING,
                    )
                )
            }
        } catch (e: IllegalArgumentException) {
            expect {
                that(e.message)
                    .isEqualTo("Expected IndexingResult.BlockResult (full block result required)")
            }
        }

        verify { historyService wasNot Called }
    }

    @Test
    fun `process - if processEvents returns records then save should be called`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_NO_CLAUSES
        val records = listOf<IndexedHistoryEvent>(mockk<IndexedHistoryEvent>())

        coEvery { historyService.processEvents(events, block) } returns records
        every { historyService.save(records) } returns Unit

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processEvents(events, block) }
        verify(exactly = 1) { historyService.save(records) }
    }
}
