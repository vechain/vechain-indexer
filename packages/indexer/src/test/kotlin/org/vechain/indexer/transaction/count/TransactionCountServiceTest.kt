package org.vechain.indexer.transaction.count

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.transaction.TransactionCountSummary
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@ExtendWith(MockKExtension::class)
class TransactionCountServiceTest {
    @MockK lateinit var repository: TransactionCountSummaryRepository
    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var service: TransactionCountService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            TransactionCountService(
                repository,
                mongoTemplate,
                InlineVersioningProperties().apply {
                    blockWindow = 10_000
                    maxVersions = 100
                },
            )
    }

    @Test
    fun `processBlock creates genesis record with counts from the block alone`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE.copy(number = 0L)

        val processingResult = service.processBlock(block)
        val result = processingResult.current

        assertNull(processingResult.previous)
        assertTrue(processingResult.shouldPersist)
        assertEquals(block.id, result.blockId)
        assertEquals(0L, result.blockNumber)
        assertEquals(1, result.version)
        assertEquals(TransactionCountSummary.SUMMARY_ID, result.id)
        assertEquals(BigInteger.ONE, result.totalTransactions)
        assertEquals(BigInteger.ONE, result.totalClauses)
        assertEquals(BigInteger.ZERO, result.totalRevertedTransactions)
        assertEquals(BigInteger.ZERO, result.totalRevertedClauses)
    }

    @Test
    fun `processBlock accumulates counts for non-genesis block`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previous =
            createSummary(
                blockNumber = block.number - 1,
                totalTransactions = BigInteger.valueOf(10),
                totalClauses = BigInteger.valueOf(20),
                totalRevertedTransactions = BigInteger.valueOf(2),
                totalRevertedClauses = BigInteger.valueOf(3),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns previous

        val processingResult = service.processBlock(block)
        val result = processingResult.current

        assertEquals(previous, processingResult.previous)
        assertTrue(processingResult.shouldPersist)
        assertEquals(previous.version + 1, result.version)
        assertEquals(BigInteger.valueOf(11), result.totalTransactions)
        assertEquals(BigInteger.valueOf(21), result.totalClauses)
        assertEquals(BigInteger.valueOf(2), result.totalRevertedTransactions)
        assertEquals(BigInteger.valueOf(3), result.totalRevertedClauses)
        verify(exactly = 1) { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) }
    }

    @Test
    fun `processBlock uses cached previous summary for sequential processing`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previousForFirstBlock =
            createSummary(
                blockNumber = block.number - 1,
                totalTransactions = BigInteger.valueOf(5),
                totalClauses = BigInteger.valueOf(8),
                totalRevertedTransactions = BigInteger.ONE,
                totalRevertedClauses = BigInteger.valueOf(2),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns
            previousForFirstBlock

        val firstResult = service.processBlock(block)
        val first = firstResult.current
        val next = block.copy(number = block.number + 1, id = "0xnext")
        val nextResult = service.processBlock(next)
        val result = nextResult.current

        assertTrue(firstResult.shouldPersist)
        assertTrue(nextResult.shouldPersist)
        assertEquals(first, nextResult.previous)
        assertEquals(first.totalTransactions + BigInteger.ONE, result.totalTransactions)
        assertEquals(first.totalClauses + BigInteger.ONE, result.totalClauses)
        assertEquals(first.totalRevertedTransactions, result.totalRevertedTransactions)
        assertEquals(first.totalRevertedClauses, result.totalRevertedClauses)
        // Cached path should avoid a second repository lookup for the next block
        verify(exactly = 1) { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) }
    }

    @Test
    fun `processBlock skips persistence for empty block but advances cached state`() {
        val firstBlock = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previous =
            createSummary(
                blockNumber = firstBlock.number - 1,
                totalTransactions = BigInteger.valueOf(5),
                totalClauses = BigInteger.valueOf(8),
                totalRevertedTransactions = BigInteger.ONE,
                totalRevertedClauses = BigInteger.valueOf(2),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns previous

        val first = service.processBlock(firstBlock).current
        val emptyBlock =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = firstBlock.number + 1,
                id = "0xempty",
                parentID = firstBlock.id,
            )

        val result = service.processBlock(emptyBlock)

        assertEquals(first.totalTransactions, result.current.totalTransactions)
        assertEquals(first.totalClauses, result.current.totalClauses)
        assertEquals(first.totalRevertedTransactions, result.current.totalRevertedTransactions)
        assertEquals(first.totalRevertedClauses, result.current.totalRevertedClauses)
        assertEquals(emptyBlock.number, result.current.blockNumber)
        assertEquals(emptyBlock.id, result.current.blockId)
        assertEquals(first.version, result.current.version)
        assertEquals(first, result.previous)
        assertFalse(result.shouldPersist)
        verify(exactly = 1) { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) }
    }

    @Test
    fun `processBlock accumulates correctly after skipped empty block`() {
        val firstBlock = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previous =
            createSummary(
                blockNumber = firstBlock.number - 1,
                totalTransactions = BigInteger.valueOf(5),
                totalClauses = BigInteger.valueOf(8),
                totalRevertedTransactions = BigInteger.ONE,
                totalRevertedClauses = BigInteger.valueOf(2),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns previous

        val first = service.processBlock(firstBlock).current
        val emptyBlock =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = firstBlock.number + 1,
                id = "0xempty",
                parentID = firstBlock.id,
            )
        val skipped = service.processBlock(emptyBlock).current
        val nextBlock =
            BlockFixtures.BLOCK_NFT_MINT_REVERTED.copy(
                number = emptyBlock.number + 1,
                parentID = emptyBlock.id,
            )

        val result = service.processBlock(nextBlock)

        assertEquals(first, result.previous)
        assertEquals(first.blockNumber, result.previous?.blockNumber)
        assertEquals(first.version, result.previous?.version)
        assertEquals(emptyBlock.number, skipped.blockNumber)
        assertTrue(result.shouldPersist)
        assertEquals(
            first.totalTransactions + BigInteger.valueOf(2),
            result.current.totalTransactions,
        )
        assertEquals(first.totalClauses + BigInteger.valueOf(2), result.current.totalClauses)
        assertEquals(
            first.totalRevertedTransactions + BigInteger.ONE,
            result.current.totalRevertedTransactions,
        )
        assertEquals(
            first.totalRevertedClauses + BigInteger.ONE,
            result.current.totalRevertedClauses,
        )
        verify(exactly = 1) { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) }
    }

    @Test
    fun `processBlock preserves last persisted version after skipped empty block`() {
        val firstBlock = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val previous =
            createSummary(
                blockNumber = firstBlock.number - 1,
                totalTransactions = BigInteger.valueOf(5),
                totalClauses = BigInteger.valueOf(8),
                totalRevertedTransactions = BigInteger.ONE,
                totalRevertedClauses = BigInteger.valueOf(2),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns previous

        val first = service.processBlock(firstBlock).current
        val emptyBlock =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = firstBlock.number + 1,
                id = "0xempty",
                parentID = firstBlock.id,
            )
        service.processBlock(emptyBlock)
        val nextBlock =
            BlockFixtures.BLOCK_SINGLE_CLAUSE.copy(
                number = emptyBlock.number + 1,
                id = "0xnext",
                parentID = emptyBlock.id,
            )

        val result = service.processBlock(nextBlock)

        assertEquals(first, result.previous)
        assertEquals(first.blockId, result.previous?.blockId)
        assertEquals(first.blockNumber, result.previous?.blockNumber)
        assertEquals(first.version, result.previous?.version)
        verify(exactly = 1) { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) }
    }

    @Test
    fun `processBlock accumulates reverted transaction and clause totals`() {
        val block = BlockFixtures.BLOCK_NFT_MINT_REVERTED
        val previous =
            createSummary(
                blockNumber = block.number - 1,
                totalTransactions = BigInteger.valueOf(20),
                totalClauses = BigInteger.valueOf(30),
                totalRevertedTransactions = BigInteger.valueOf(4),
                totalRevertedClauses = BigInteger.valueOf(5),
            )
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns previous

        val result = service.processBlock(block).current

        assertEquals(BigInteger.valueOf(22), result.totalTransactions)
        assertEquals(BigInteger.valueOf(32), result.totalClauses)
        assertEquals(BigInteger.valueOf(5), result.totalRevertedTransactions)
        assertEquals(BigInteger.valueOf(6), result.totalRevertedClauses)
    }

    @Test
    fun `processBlock throws when previous summary is missing for non-genesis block`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        every { repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID) } returns null

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
    fun `processBlock returns non-null for genesis with no transactions`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 0L)

        val processingResult = service.processBlock(block)
        val result = processingResult.current

        assertTrue(processingResult.shouldPersist)
        assertNotNull(result)
        assertEquals(BigInteger.ZERO, result.totalTransactions)
        assertEquals(BigInteger.ZERO, result.totalClauses)
        assertEquals(BigInteger.ZERO, result.totalRevertedTransactions)
        assertEquals(BigInteger.ZERO, result.totalRevertedClauses)
    }

    private fun createSummary(
        blockNumber: Long = 1L,
        blockTimestamp: Long = 1_000L,
        totalTransactions: BigInteger = BigInteger.ZERO,
        totalClauses: BigInteger = BigInteger.ZERO,
        totalRevertedTransactions: BigInteger = BigInteger.ZERO,
        totalRevertedClauses: BigInteger = BigInteger.ZERO,
    ) =
        TransactionCountSummary(
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            version = 1,
            totalTransactions = totalTransactions,
            totalClauses = totalClauses,
            totalRevertedTransactions = totalRevertedTransactions,
            totalRevertedClauses = totalRevertedClauses,
        )
}
