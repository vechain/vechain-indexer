package org.vechain.indexer.transaction

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures

@ExtendWith(MockKExtension::class)
class TransactionServiceTest {

    @Test
    fun `processBlock builds canonical transaction indexes`() {
        val block = BlockFixtures.BLOCK_MULTIPLE_TXS

        val indexedTransactions = TransactionService().processBlock(block, emptyList())

        assertEquals(block.transactions.map { it.id }, indexedTransactions.map { it.id })
        assertEquals(
            block.transactions.indices.map { it.toLong() },
            indexedTransactions.map { it.transactionIndex },
        )
    }
}
