package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.thor.Address
import strikt.api.expectThat
import strikt.assertions.isSameInstanceAs

class TransactionServiceTest {
    private val transactionRepository: TransactionRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val service = TransactionService(transactionRepository, mongoTemplate)

    @Test
    fun `findByOriginOrDelegator delegates to origin query when includeDelegated is false`() {
        val pageable = Pageable.ofSize(10)
        val expected = SliceImpl<IndexedTransaction>(emptyList(), pageable, false)
        val address = Address("0x0000000000000000000000000000000000000001")
        every { transactionRepository.findByOrigin(address.value, pageable) } returns expected

        expectThat(service.findByOriginOrDelegator(address, false, pageable))
            .isSameInstanceAs(expected)
    }

    @Test
    fun `findLatest returns canonical page and cursor`() {
        val results =
            listOf(
                transaction(id = "0x1", blockNumber = 101L, transactionIndex = 0L),
                transaction(id = "0x2", blockNumber = 101L, transactionIndex = 1L),
                transaction(id = "0x3", blockNumber = 100L, transactionIndex = 0L),
            )
        every { mongoTemplate.find(any<Query>(), IndexedTransaction::class.java) } returns results

        val response = service.findLatest(size = 2, cursor = null)

        assertEquals(listOf("0x1", "0x2"), response.data.map { it.id })
        assertTrue(response.pagination.hasNext)
        assertEquals("101|1", response.pagination.cursor)
    }

    @Test
    fun `findLatest applies cursor filter using transaction index tiebreaker`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), IndexedTransaction::class.java) } returns
            emptyList()

        service.findLatest(size = 20, cursor = "101|1")

        val query = querySlot.captured.toString()
        assertTrue(query.contains("blockNumber"))
        assertTrue(query.contains("transactionIndex"))
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
