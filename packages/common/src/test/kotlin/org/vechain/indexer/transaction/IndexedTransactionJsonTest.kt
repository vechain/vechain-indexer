package org.vechain.indexer.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IndexedTransactionJsonTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `transaction index is not serialized to JSON`() {
        val transaction =
            IndexedTransaction(
                id = "0x1",
                blockId = "0xblock",
                blockNumber = 1L,
                blockTimestamp = 1_700_000_000L,
                transactionIndex = 7L,
                type = null,
                size = 1L,
                chainTag = 1L,
                blockRef = "0xblockref",
                expiration = 1L,
                clauses = emptyList(),
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

        val json = objectMapper.writeValueAsString(transaction)

        assertFalse(json.contains("transactionIndex"))
    }
}
