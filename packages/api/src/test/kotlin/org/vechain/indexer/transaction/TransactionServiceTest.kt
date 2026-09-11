package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.blocks.BlocksReadRepository
import org.vechain.indexer.blocks.BlocksReadRepository.LatestCursor
import org.vechain.indexer.thor.Address

class TransactionServiceTest {
    private val repository: BlocksReadRepository = mockk()
    private val service = TransactionService(repository)
    private val address = Address("0x0000000000000000000000000000000000000001")

    @Test
    fun `findByOriginOrDelegator pages with one row past the page and the requested direction`() {
        val pageable = PageRequest.of(2, 2, Sort.by(Direction.ASC, "blockNumber", "_id"))
        val rows =
            listOf(
                transaction("0x1", 100L, 0L),
                transaction("0x2", 101L, 0L),
                transaction("0x3", 102L, 0L),
            )
        every { repository.findByOrigin(address.value, true, 4L, 3, Direction.ASC, false) } returns
            rows

        val slice = service.findByOriginOrDelegator(address, true, pageable, expanded = false)

        assertEquals(listOf("0x1", "0x2"), slice.content.map { it.id })
        assertTrue(slice.hasNext())
    }

    @Test
    fun `a short page has no next`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Direction.DESC, "blockNumber", "_id"))
        every { repository.findDelegated(address.value, 0L, 21, Direction.DESC, true) } returns
            listOf(transaction("0x1", 100L, 0L))

        val slice = service.findAllDelegated(address, pageable, expanded = true)

        assertEquals(1, slice.content.size)
        assertFalse(slice.hasNext())
    }

    @Test
    fun `findByContractAddress forwards offset, limit and expansion`() {
        val pageable = PageRequest.of(1, 5, Sort.by(Direction.DESC, "blockNumber", "_id"))
        every { repository.findByContract(address.value, 5L, 6, Direction.DESC, false) } returns
            emptyList()

        assertTrue(
            service.findByContractAddress(address, pageable, expanded = false).content.isEmpty()
        )
    }

    @Test
    fun `findLatest returns canonical page and cursor`() {
        every { repository.findLatest(null, 3) } returns
            listOf(
                transaction(id = "0x1", blockNumber = 101L, transactionIndex = 0L),
                transaction(id = "0x2", blockNumber = 101L, transactionIndex = 1L),
                transaction(id = "0x3", blockNumber = 100L, transactionIndex = 0L),
            )

        val response = service.findLatest(size = 2, cursor = null)

        assertEquals(listOf("0x1", "0x2"), response.data.map { it.id })
        assertTrue(response.pagination.hasNext)
        assertEquals("101|1", response.pagination.cursor)
    }

    @Test
    fun `findLatest continues after the cursor's block and transaction index`() {
        every { repository.findLatest(LatestCursor(101L, 1), 21) } returns
            listOf(transaction("0x3", 100L, 0L))

        val response = service.findLatest(size = 20, cursor = "101|1")

        assertEquals(listOf("0x3"), response.data.map { it.id })
        assertFalse(response.pagination.hasNext)
        assertNull(response.pagination.cursor)
    }

    private fun transaction(
        id: String,
        blockNumber: Long,
        transactionIndex: Long,
    ): IndexedTransaction =
        IndexedTransaction(
            id = id,
            blockId = "0xblock",
            blockNumber = blockNumber,
            blockTimestamp = 1_700_000_000L,
            transactionIndex = transactionIndex,
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
}
