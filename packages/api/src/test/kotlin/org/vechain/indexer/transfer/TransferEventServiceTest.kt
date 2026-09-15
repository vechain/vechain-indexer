package org.vechain.indexer.transfer

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.thor.Address

class TransferEventServiceTest {

    private val repository: TransferReadRepository = mockk()
    private val officialTokenService: OfficialTokenService = mockk()
    private val service = TransferEventService(repository, officialTokenService)

    private val wallet = "0x" + "a".repeat(40)
    private val token = "0x" + "1".repeat(40)

    private fun pageable(page: Int, size: Int, vararg fields: String) =
        PageRequest.of(page, size, Sort.by(Direction.DESC, *fields))

    @Test
    fun `findLatestByType returns canonical page and cursor`() {
        every { repository.findLatest(listOf(TransferEventType.FUNGIBLE_TOKEN), null, 3) } returns
            listOf(
                transfer(id = "transfer-1", blockNumber = 101L, transferIndex = 0L),
                transfer(id = "transfer-2", blockNumber = 101L, transferIndex = 1L),
                transfer(id = "transfer-3", blockNumber = 100L, transferIndex = 0L),
            )

        val response =
            service.findLatestByType(
                eventTypes = listOf(TransferEventType.FUNGIBLE_TOKEN),
                size = 2,
                cursor = null,
            )

        assertEquals(listOf("transfer-1", "transfer-2"), response.data.map { it.id })
        assertTrue(response.pagination.hasNext)
        assertEquals("101|1", response.pagination.cursor)
    }

    @Test
    fun `findLatestByType hands the parsed cursor and one row past the page to the repository`() {
        every {
            repository.findLatest(listOf(TransferEventType.NFT), LatestTransferCursor(101, 1), 21)
        } returns listOf(transfer(id = "transfer-4", blockNumber = 99L, transferIndex = 0L))

        val response = service.findLatestByType(listOf(TransferEventType.NFT), 20, "101|1")

        assertEquals(listOf("transfer-4"), response.data.map { it.id })
        assertFalse(response.pagination.hasNext)
        assertNull(response.pagination.cursor)
    }

    @Test
    fun `a cursor that is not two numbers is a bad request`() {
        assertThrows<BadRequestException> {
            service.findLatestByType(listOf(TransferEventType.NFT), 20, "101|x")
        }
    }

    @Test
    fun `find pages by time and passes every filter, a full page reporting one more`() {
        every {
            repository.find(
                null,
                null,
                wallet,
                token,
                listOf(TransferEventType.VET),
                5L,
                9L,
                2,
                3,
                Direction.DESC,
            )
        } returns listOf(transfer("t1", 3, 0), transfer("t2", 2, 0), transfer("t3", 1, 0))

        val result =
            service.find(
                toOrFrom = Address(wallet),
                tokenAddress = Address(token),
                eventTypes = listOf(TransferEventType.VET),
                after = 5L,
                before = 9L,
                pageable = pageable(1, 2, "blockTimestamp", "txId", "_id"),
            )

        assertEquals(listOf("t1", "t2"), result.content.map { it.id })
        assertTrue(result.hasNext())
    }

    @Test
    fun `official tokens only narrows the contracts to the registry`() {
        every { officialTokenService.getOfficialTokenAddresses() } returns listOf(token)
        every {
            repository.findInteractedContracts(wallet, listOf(token), 0, 21, Direction.DESC)
        } returns listOf(token)

        val result =
            service.findFungibleTokensContractsByAddress(
                Address(wallet),
                officialTokensOnly = true,
                pageable = pageable(0, 20, "blockNumber", "txId", "_id"),
            )

        assertEquals(listOf(token), result.content)
        assertFalse(result.hasNext())
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
