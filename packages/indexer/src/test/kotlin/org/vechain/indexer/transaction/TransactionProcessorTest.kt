package org.vechain.indexer.transaction

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
internal class TransactionProcessorTest {
    @MockK lateinit var transactionRepository: TransactionRepository

    @MockK lateinit var transactionService: TransactionService

    @MockK lateinit var transactionProcessor: TransactionProcessor

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        transactionProcessor =
            TransactionProcessor(
                transactionService = transactionService,
                repository = transactionRepository,
                indexerVersionService = indexerVersionService,
            )
    }

    @Test
    fun `process - should throw if block is null`() {

        assertThrows<IllegalArgumentException> {
            runBlocking {
                transactionProcessor.process(
                    IndexingResult.EventsOnly(100, emptyList(), Status.SYNCING)
                )
            }
        }
    }

    @Test
    fun `process - If no transactions shouldn't do anything`() {
        runBlocking {
            transactionProcessor.process(
                IndexingResult.Normal(
                    BlockFixtures.BLOCK_NO_CLAUSES,
                    emptyList(),
                    emptyList(),
                    Status.FULLY_SYNCED,
                )
            )
        }

        verify { transactionService wasNot Called }
    }

    @Test
    fun `process - should call service when transactions are present`() {
        val events = emptyList<IndexedEvent>()
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE

        every { transactionService.processBlockTransactions(events, block) } just Runs

        runBlocking {
            transactionProcessor.process(
                IndexingResult.Normal(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        verify { transactionService.processBlockTransactions(events, block) }
    }

    @Test
    fun `process - should call service when transactions and events are present`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE

        every { transactionService.processBlockTransactions(events, block) } just Runs

        runBlocking {
            transactionProcessor.process(
                IndexingResult.Normal(block, events, emptyList(), Status.FULLY_SYNCED)
            )
        }

        verify { transactionService.processBlockTransactions(events, block) }
    }
}
