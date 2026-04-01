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
    fun `process - if no events or transactions are present then processBlock should still be called`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES

        coEvery { historyService.processBlock(emptyList(), block, emptyList()) } returns emptyList()

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processBlock(emptyList(), block, emptyList()) }
        verify(exactly = 0) { historyService.save(any()) }
    }

    @Test
    fun `processEvents - if events are present but no transactions then processBlockEvents should be called`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_NO_CLAUSES

        coEvery { historyService.processBlock(events, block, emptyList()) } returns emptyList()

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) {
            historyService.processBlock(events, BlockFixtures.BLOCK_NO_CLAUSES, emptyList())
        }
    }

    @Test
    fun `process - if transactions are present but no events then processBlockEvents should be called`() {
        val block = BLOCK_SINGLE_CLAUSE
        val events = emptyList<IndexedEvent>()

        coEvery { historyService.processBlock(events, block, emptyList()) } returns emptyList()

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processBlock(events, block, emptyList()) }
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

        coEvery { historyService.processBlock(events, block, emptyList()) } returns records
        every { historyService.save(records) } returns Unit

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processBlock(events, block, emptyList()) }
        verify(exactly = 1) { historyService.save(records) }
    }

    @Test
    fun `process - empty block saves synthetic delegate active history rows`() {
        assertSyntheticLifecycleHistoryIsSavedOnEmptyBlock(
            HistoryEventName.STARGATE_DELEGATE_ACTIVE
        )
    }

    @Test
    fun `process - empty block saves synthetic validator exit history rows`() {
        assertSyntheticLifecycleHistoryIsSavedOnEmptyBlock(
            HistoryEventName.STARGATE_DELEGATION_EXITED_VALIDATOR
        )
    }

    @Test
    fun `process - empty block saves synthetic delegation exited history rows`() {
        assertSyntheticLifecycleHistoryIsSavedOnEmptyBlock(
            HistoryEventName.STARGATE_DELEGATION_EXITED
        )
    }

    private fun assertSyntheticLifecycleHistoryIsSavedOnEmptyBlock(eventName: HistoryEventName) {
        val block = BlockFixtures.BLOCK_NO_CLAUSES
        val returnedRecords = listOf(syntheticHistoryEvent(block, eventName))

        coEvery { historyService.processBlock(emptyList(), block, emptyList()) } returns
            returnedRecords
        every { historyService.save(any()) } just Runs

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.FULLY_SYNCED)
            )
        }

        coVerify(exactly = 1) { historyService.processBlock(emptyList(), block, emptyList()) }
        verify(exactly = 1) {
            historyService.save(match { it.singleOrNull()?.eventName == eventName })
        }
    }

    private fun syntheticHistoryEvent(
        block: org.vechain.indexer.thor.model.Block,
        eventName: HistoryEventName,
    ) =
        IndexedHistoryEvent(
            id = "history-${eventName.name}",
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            txId = "tx-${eventName.name}",
            eventName = eventName,
        )
}
