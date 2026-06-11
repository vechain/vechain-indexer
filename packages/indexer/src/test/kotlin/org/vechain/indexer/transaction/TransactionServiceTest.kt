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
    fun `processBlock builds canonical transaction indexes`() {
        val block = BlockFixtures.BLOCK_MULTIPLE_TXS

        val indexedTransactions = TransactionService(mongoTemplate).processBlock(block, emptyList())

        assertEquals(block.transactions.map { it.id }, indexedTransactions.map { it.id })
        assertEquals(
            block.transactions.indices.map { it.toLong() },
            indexedTransactions.map { it.transactionIndex },
        )
    }

    @Test
    fun `save persists the provided records via mongoTemplate`() {
        val recordsSlot = slot<List<IndexedTransaction>>()
        every { mongoTemplate.insert(capture(recordsSlot), IndexedTransaction::class.java) } returns
            emptyList<IndexedTransaction>()
        val service = TransactionService(mongoTemplate)
        val records = service.processBlock(BlockFixtures.BLOCK_MULTIPLE_TXS, emptyList())

        service.save(records)

        assertEquals(records, recordsSlot.captured)
    }
}
