package org.vechain.indexer.transfer

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class TransferEventServiceTest {

    private val transferEventRepository: TransferEventRepository = mockk()
    private val fungibleTokenInteractionsRepository: FungibleTokenInteractionsRepository = mockk()
    private val officialTokenService: OfficialTokenService = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val service =
        TransferEventService(
            transferEventRepository,
            fungibleTokenInteractionsRepository,
            officialTokenService,
            mongoTemplate,
        )

    @Test
    fun `findLatestByType returns canonical page and cursor`() {
        val results =
            listOf(
                transfer(id = "transfer-1", blockNumber = 101L, transferIndex = 0L),
                transfer(id = "transfer-2", blockNumber = 101L, transferIndex = 1L),
                transfer(id = "transfer-3", blockNumber = 100L, transferIndex = 0L),
            )
        every { mongoTemplate.find(any<Query>(), IndexedTransferEvent::class.java) } returns results

        val response =
            service.findLatestByType(
                eventType = TransferEventType.FUNGIBLE_TOKEN,
                size = 2,
                cursor = null,
            )

        assertEquals(listOf("transfer-1", "transfer-2"), response.data.map { it.id })
        assertTrue(response.pagination.hasNext)
        assertEquals("101|1", response.pagination.cursor)
    }

    @Test
    fun `findLatestByType applies event type and cursor filter using transfer index tiebreaker`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), IndexedTransferEvent::class.java) } returns
            emptyList()

        service.findLatestByType(eventType = TransferEventType.NFT, size = 20, cursor = "101|1")

        val query = querySlot.captured.toString()
        assertTrue(query.contains("eventType"))
        assertTrue(query.contains("NFT"))
        assertTrue(query.contains("blockNumber"))
        assertTrue(query.contains("transferIndex"))
    }

    private fun transfer(
        id: String,
        blockNumber: Long,
        transferIndex: Long,
        eventType: TransferEventType = TransferEventType.FUNGIBLE_TOKEN,
    ): IndexedTransferEvent =
        IndexedTransferEvent(
            id = id,
            blockId = "block-$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = 1_700_000_000L,
            transferIndex = transferIndex,
            txId = "tx-$id",
            from = "0x0000000000000000000000000000000000000001",
            to = "0x0000000000000000000000000000000000000002",
            value = "1",
            tokenAddress = "0x0000000000000000000000000000000000000003",
            tokenId = null,
            topics = emptyList(),
            eventType = eventType,
        )
}
