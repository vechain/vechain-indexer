package org.vechain.indexer.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Views

class IndexedTransactionJsonTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `transaction index is not serialized to JSON`() {
        val transaction = transaction(transactionIndex = 7L)

        val json = objectMapper.writeValueAsString(transaction)

        assertFalse(json.contains("transactionIndex"))
    }

    @Test
    fun `public transaction JSON includes clause count but not clauses`() {
        val transaction =
            transaction(
                clauses =
                    listOf(
                        Clause("0x0000000000000000000000000000000000000001", "0x0", "0x"),
                        Clause("0x0000000000000000000000000000000000000002", "0x0", "0x"),
                    )
            )

        val json =
            objectMapper.writerWithView(Views.Public::class.java).writeValueAsString(transaction)
        val tree = objectMapper.readTree(json)

        assertEquals(2, tree["clauseCount"].intValue())
        assertFalse(tree.has("clauses"))
    }

    private fun transaction(
        transactionIndex: Long = 0L,
        clauses: List<Clause> = emptyList(),
    ): IndexedTransaction =
        IndexedTransaction(
            id = "0x1",
            blockId = "0xblock",
            blockNumber = 1L,
            blockTimestamp = 1_700_000_000L,
            transactionIndex = transactionIndex,
            type = null,
            size = 1L,
            chainTag = 1L,
            blockRef = "0xblockref",
            expiration = 1L,
            clauses = clauses,
            gasPriceCoef = null,
            gas = 1L,
            maxFeePerGas = null,
            maxPriorityFeePerGas = null,
            dependsOn = null,
            nonce = "0x1",
            gasUsed = 1L,
            gasPayer = "0x0000000000000000000000000000000000000001",
            paid = "0x0",
            reward = "0x0",
            reverted = false,
            origin = "0x0000000000000000000000000000000000000001",
            outputs = emptyList(),
        )
}
