package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.fixtures.BlockFixtures

@ExtendWith(MockKExtension::class)
class TransactionServiceTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @Test
    fun `processBlockTransactions stores canonical transaction indexes`() {
        val transactionsSlot = slot<List<IndexedTransaction>>()
        every {
            mongoTemplate.insert(capture(transactionsSlot), IndexedTransaction::class.java)
        } returns emptyList<IndexedTransaction>()
        val block = BlockFixtures.BLOCK_MULTIPLE_TXS

        TransactionService(mongoTemplate).processBlockTransactions(emptyList(), block)

        val indexedTransactions = transactionsSlot.captured
        assertEquals(block.transactions.map { it.id }, indexedTransactions.map { it.id })
        assertEquals(
            block.transactions.indices.map { it.toLong() },
            indexedTransactions.map { it.transactionIndex },
        )
    }
}
