package org.vechain.indexer.transfer

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IndexedTransferEventJsonTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `transfer index is not serialized to JSON`() {
        val transfer =
            IndexedTransferEvent(
                id = "transfer-1",
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1_700_000_000L,
                transferIndex = 7L,
                txId = "tx-1",
                from = "0x0000000000000000000000000000000000000001",
                to = "0x0000000000000000000000000000000000000002",
                value = "1",
                tokenAddress = "0x0000000000000000000000000000000000000003",
                tokenId = "1",
                topics = emptyList(),
                eventType = TransferEventType.NFT,
            )

        val json = objectMapper.writeValueAsString(transfer)

        assertFalse(json.contains("transferIndex"))
    }
}
