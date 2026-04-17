package org.vechain.indexer.transaction.count

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.transaction.TransactionCountSummary
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@ExtendWith(MockKExtension::class)
class TransactionCountServiceTest {
    @MockK lateinit var repository: TransactionCountSummaryRepository

    private lateinit var service: TransactionCountService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TransactionCountService(repository)
    }

    @Test
    fun `processBlock creates genesis record with counts from the block alone`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE.copy(number = 0L)

        val result = service.processBlock(block)

        assertEquals(block.id, result.blockId)
        assertEquals(0L, result.blockNumber)
        assertEquals(BigInteger.ONE, result.totalTransactions)
        assertEquals(BigInteger.ONE, result.totalClauses)
    }

    @Test
    fun `processBlock accumulates counts for non-genesis block`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previous =
            createSummary(
                blockNumber = block.number - 1,
                totalTransactions = BigInteger.valueOf(10),
                totalClauses = BigInteger.valueOf(20),
            )
        every { repository.findByIdOrNull((block.number - 1).toString()) } returns previous

        val result = service.processBlock(block)

        assertEquals(BigInteger.valueOf(11), result.totalTransactions)
        assertEquals(BigInteger.valueOf(21), result.totalClauses)
        verify(exactly = 1) { repository.findByIdOrNull((block.number - 1).toString()) }
    }

    @Test
    fun `processBlock uses cached previous summary for sequential processing`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previousForFirstBlock =
            createSummary(
                blockNumber = block.number - 1,
                totalTransactions = BigInteger.valueOf(5),
                totalClauses = BigInteger.valueOf(8),
            )
        every { repository.findByIdOrNull((block.number - 1).toString()) } returns
            previousForFirstBlock

        val first = service.processBlock(block)
        val next = block.copy(number = block.number + 1, id = "0xnext")
        val result = service.processBlock(next)

        assertEquals(first.totalTransactions + BigInteger.ONE, result.totalTransactions)
        assertEquals(first.totalClauses + BigInteger.ONE, result.totalClauses)
        // Cached path should avoid a second repository lookup for the next block
        verify(exactly = 1) { repository.findByIdOrNull((block.number - 1).toString()) }
    }

    @Test
    fun `processBlock throws when previous summary is missing for non-genesis block`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        every { repository.findByIdOrNull((block.number - 1).toString()) } returns null

        val exception = assertThrows<IllegalArgumentException> { service.processBlock(block) }

        assertTrue(
            exception.message!!.contains(
                "Previous transaction count summary should exist for block ${block.number}"
            )
        )
    }

    @Test
    fun `getPreviousSummary returns null for genesis block`() {
        assertNull(service.getPreviousSummary(0L))
    }

    @Test
    fun `save delegates to repository`() {
        val summary = createSummary()
        every { repository.save(summary) } returns summary

        service.save(summary)

        verify(exactly = 1) { repository.save(summary) }
    }

    @Test
    fun `processBlock returns non-null for genesis with no transactions`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 0L)

        val result = service.processBlock(block)

        assertNotNull(result)
        assertEquals(BigInteger.ZERO, result.totalTransactions)
        assertEquals(BigInteger.ZERO, result.totalClauses)
    }

    private fun createSummary(
        blockNumber: Long = 1L,
        blockTimestamp: Long = 1_000L,
        totalTransactions: BigInteger = BigInteger.ZERO,
        totalClauses: BigInteger = BigInteger.ZERO,
    ) =
        TransactionCountSummary(
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            totalTransactions = totalTransactions,
            totalClauses = totalClauses,
        )
}
