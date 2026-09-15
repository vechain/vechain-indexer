package org.vechain.indexer.transfer

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.model.generic.RawEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS

class TransferServiceTest {

    private val service = TransferService(mockk<TransferWriteRepository>())

    @Test
    fun `processEvents assigns transfer indexes per block in event order`() {
        val events =
            listOf(
                transferEvent(id = "event-1", blockNumber = 10L, params = nftParams("1")),
                transferEvent(id = "event-2", blockNumber = 10L, params = fungibleParams("100")),
                transferEvent(id = "event-3", blockNumber = 11L, params = nftParams("2")),
            )

        val transfers = service.processEvents(events).transfers

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

        val transfers = service.processEvents(events).transfers

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

    @Test
    fun `a VET transfer keeps its amount and has no token`() {
        val event =
            transferEvent(
                id = "vet",
                blockNumber = 10L,
                eventType = "VET_TRANSFER",
                address = null,
                params = mapOf("from" to "0xfrom", "to" to "0xto", "amount" to "10000000"),
            )

        val transfer = service.processEvents(listOf(event)).transfers.single()

        assertEquals(TransferEventType.VET, transfer.eventType)
        assertEquals("10000000", transfer.value)
        assertEquals(null, transfer.tokenAddress)
    }

    @Test
    fun `fungible transfers record both wallets once per contract, VTHO and the zero address aside`() {
        val events =
            listOf(
                transferEvent(id = "e1", blockNumber = 10L, params = fungibleParams("1")),
                transferEvent(
                    id = "e2",
                    blockNumber = 11L,
                    params = fungibleParams("2", from = "0xto", to = "0xfrom"),
                ),
                transferEvent(
                    id = "mint",
                    blockNumber = 11L,
                    params = fungibleParams("3", from = Address.ZERO_ADDRESS, to = "0xminted"),
                ),
                transferEvent(
                    id = "vtho",
                    blockNumber = 11L,
                    address = VTHO_CONTRACT_ADDRESS,
                    params = fungibleParams("4", from = "0xvtho1", to = "0xvtho2"),
                ),
                transferEvent(id = "nft", blockNumber = 11L, params = nftParams("5")),
            )

        val interactions = service.processEvents(events).interactions

        assertEquals(
            listOf("0xfrom" to 10L, "0xto" to 10L, "0xminted" to 11L),
            interactions.map { it.walletAddress to it.blockNumber },
        )
        assertEquals(setOf("0xtoken"), interactions.map { it.contractAddress }.toSet())
    }

    private fun transferEvent(
        id: String,
        blockNumber: Long,
        params: Map<String, Any>,
        eventType: String = "Transfer",
        address: String? = "0xtoken",
    ): IndexedEvent =
        IndexedEventsFixtures.buildIndexedEvent(
            id = id,
            blockId = "block-$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = 1_700_000_000L + blockNumber,
            txId = "tx-$id",
            raw = RawEvent(data = "0x", topics = listOf("topic")),
            params = AbiEventParameters(params, eventType),
            address = address,
            eventType = eventType,
        )

    private fun nftParams(tokenId: String): Map<String, Any> =
        mapOf("from" to "0xfrom", "to" to "0xto", "tokenId" to tokenId)

    private fun fungibleParams(
        value: String,
        from: String = "0xfrom",
        to: String = "0xto",
    ): Map<String, Any> = mapOf("from" to from, "to" to to, "value" to value)
}
