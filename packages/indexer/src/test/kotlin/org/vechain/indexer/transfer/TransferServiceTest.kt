package org.vechain.indexer.transfer

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.model.generic.RawEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures

class TransferServiceTest {

    private val repository: TransferEventRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val service = TransferService(repository, mongoTemplate)

    @Test
    fun `processEvents assigns transfer indexes per block in event order`() {
        val events =
            listOf(
                transferEvent(id = "event-1", blockNumber = 10L, params = nftParams("1")),
                transferEvent(id = "event-2", blockNumber = 10L, params = fungibleParams("100")),
                transferEvent(id = "event-3", blockNumber = 11L, params = nftParams("2")),
            )

        val transfers = service.processEvents(events)

        assertEquals(listOf(0L, 1L, 0L), transfers.map { it.transferIndex })
        assertEquals(
            listOf(TransferEventType.NFT, TransferEventType.FUNGIBLE_TOKEN, TransferEventType.NFT),
            transfers.map { it.eventType },
        )
    }

    @Test
    fun `processEvents assigns sequential transfer indexes to batch transfer rows`() {
        val events =
            listOf(
                transferEvent(id = "event-1", blockNumber = 10L, params = nftParams("1")),
                transferEvent(
                    id = "event-2",
                    blockNumber = 10L,
                    eventType = "TransferBatch",
                    params =
                        mapOf(
                            "from" to "0xfrom",
                            "to" to "0xto",
                            "ids" to listOf("2", "3"),
                            "values" to listOf("20", "30"),
                        ),
                ),
                transferEvent(id = "event-3", blockNumber = 10L, params = fungibleParams("100")),
            )

        val transfers = service.processEvents(events)

        assertEquals(listOf(0L, 1L, 2L, 3L), transfers.map { it.transferIndex })
        assertEquals(
            listOf(
                TransferEventType.NFT,
                TransferEventType.SEMI_FUNGIBLE_TOKEN,
                TransferEventType.SEMI_FUNGIBLE_TOKEN,
                TransferEventType.FUNGIBLE_TOKEN,
            ),
            transfers.map { it.eventType },
        )
    }

    private fun transferEvent(
        id: String,
        blockNumber: Long,
        params: Map<String, Any>,
        eventType: String = "Transfer",
    ): IndexedEvent =
        IndexedEventsFixtures.buildIndexedEvent(
            id = id,
            blockId = "block-$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = 1_700_000_000L + blockNumber,
            txId = "tx-$id",
            raw = RawEvent(data = "0x", topics = listOf("topic")),
            params = AbiEventParameters(params, eventType),
            address = "0xtoken",
            eventType = eventType,
        )

    private fun nftParams(tokenId: String): Map<String, Any> =
        mapOf("from" to "0xfrom", "to" to "0xto", "tokenId" to tokenId)

    private fun fungibleParams(value: String): Map<String, Any> =
        mapOf("from" to "0xfrom", "to" to "0xto", "value" to value)
}
