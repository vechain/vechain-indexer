package org.vechain.indexer.transaction

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import org.vechain.indexer.repository.TransactionRepository

@ExtendWith(MockKExtension::class)
internal class TransactionProcessorTest {
    @MockK lateinit var transactionRepository: TransactionRepository

    @MockK lateinit var transactionService: TransactionService

    @MockK lateinit var transactionProcessor: TransactionProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        transactionProcessor =
            TransactionProcessor(
                transactionService = transactionService,
                repository = transactionRepository,
            )
    }

    @Test
    fun `process - should throw if block is null`() {
        assertThrows<IllegalArgumentException> { transactionProcessor.process(emptyList(), null) }
    }

    @Test
    fun `process - If no transactions shouldn't do anything`() {
        transactionProcessor.process(emptyList(), BlockFixtures.BLOCK_NO_CLAUSES)

        verify { transactionService wasNot Called }
    }

    @Test
    fun `process - should call service when transactions are present`() {
        val events = emptyList<IndexedEvent>()
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        transactionProcessor.process(events, block)

        verify { transactionService.processBlockTransactions(events, block) }
    }

    @Test
    fun `process - should call service when transactions and events are present`() {
        val events = INDEXED_EVENTS_BLACKLIST
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        transactionProcessor.process(events, block)

        verify { transactionService.processBlockTransactions(events, block) }
    }
}
