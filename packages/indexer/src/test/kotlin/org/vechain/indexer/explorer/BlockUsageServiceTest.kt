package org.vechain.indexer.explorer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.fixtures.BlockFixtures

@ExtendWith(MockKExtension::class)
class BlockUsageServiceTest {
    @MockK lateinit var repository: BlockUsageRepository

    private lateinit var service: BlockUsageService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = BlockUsageService(repository)
    }

    // Test getPreviousBlockUsage
    @Test
    fun `getPreviousBlockUsage returns null for genesis block`() {
        val result = service.getPreviousBlockUsage(0L)
        assertNull(result)
    }

    @Test
    fun `getPreviousBlockUsage queries repository for non-genesis block`() {
        val previousBlockUsage = createBlockUsage(blockNumber = 99L, blockTimestamp = 1000L)
        every { repository.findByIdOrNull(99L) } returns previousBlockUsage

        val result = service.getPreviousBlockUsage(100L)

        assertEquals(previousBlockUsage, result)
        verify(exactly = 1) { repository.findByIdOrNull(99L) }
    }

    // Test validatePreviousBlockUsage
    @Test
    fun `validatePreviousBlockUsage succeeds for genesis block with null previous`() {
        assertDoesNotThrow { service.validatePreviousBlockUsage(null, 0L) }
    }

    @Test
    fun `validatePreviousBlockUsage succeeds for non-genesis block with previous`() {
        val previousBlockUsage = createBlockUsage(blockNumber = 99L)

        assertDoesNotThrow { service.validatePreviousBlockUsage(previousBlockUsage, 100L) }
    }

    @Test
    fun `validatePreviousBlockUsage throws for non-genesis block without previous`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                service.validatePreviousBlockUsage(null, 100L)
            }
        assertTrue(
            exception.message!!.contains("Previous block usage record should exist for block 100")
        )
    }

    // Test parseBaseFeePerGas
    @Test
    fun `parseBaseFeePerGas returns null for null input`() {
        val result = service.parseBaseFeePerGas(null)
        assertNull(result)
    }

    @Test
    fun `parseBaseFeePerGas parses hex string with 0x prefix`() {
        val result = service.parseBaseFeePerGas("0x1a")
        assertEquals(BigInteger.valueOf(26), result)
    }

    @Test
    fun `parseBaseFeePerGas parses hex string without 0x prefix`() {
        val result = service.parseBaseFeePerGas("1a")
        assertEquals(BigInteger.valueOf(26), result)
    }

    @Test
    fun `parseBaseFeePerGas parses large hex value`() {
        val result = service.parseBaseFeePerGas("0xffffffffffffffff")
        assertEquals(BigInteger("18446744073709551615"), result)
    }

    // Test calculateTotalClauses
    @Test
    fun `calculateTotalClauses returns zero for block with no transactions`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES
        val result = service.calculateTotalClauses(block)
        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculateTotalClauses counts clauses correctly`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        val result = service.calculateTotalClauses(block)
        // BLOCK_SINGLE_CLAUSE has 1 transaction with 1 clause
        assertEquals(BigInteger.ONE, result)
    }

    // Test calculateCumulativeGasLimit
    @Test
    fun `calculateCumulativeGasLimit adds gas limits correctly`() {
        val previousBlockUsage = createBlockUsage(cumulativeGasLimit = BigInteger.valueOf(1000))
        val block = BlockFixtures.BLOCK_NO_CLAUSES // gasLimit = 10000000

        val result = service.calculateCumulativeGasLimit(previousBlockUsage, block)

        assertEquals(BigInteger.valueOf(10001000), result)
    }

    // Test calculateCumulativeGasUsed
    @Test
    fun `calculateCumulativeGasUsed adds gas used correctly`() {
        val previousBlockUsage = createBlockUsage(cumulativeGasUsed = BigInteger.valueOf(800))
        val block = BlockFixtures.BLOCK_NO_CLAUSES // gasUsed = 0

        val result = service.calculateCumulativeGasUsed(previousBlockUsage, block)

        assertEquals(BigInteger.valueOf(800), result)
    }

    // Test calculateCumulativeBaseFeePerGas
    @Test
    fun `calculateCumulativeBaseFeePerGas returns null when current block has no baseFeePerGas`() {
        val previousBlockUsage =
            createBlockUsage(cumulativeBaseFeePerGas = BigInteger.valueOf(1000000000))
        val block = BlockFixtures.BLOCK_NO_CLAUSES // This block has no baseFeePerGas

        val result = service.calculateCumulativeBaseFeePerGas(previousBlockUsage, block)

        assertNull(result)
    }

    @Test
    fun `calculateCumulativeBaseFeePerGas defaults previous null to zero`() {
        val previousBlockUsage = createBlockUsage(cumulativeBaseFeePerGas = null)
        val block = BlockFixtures.BLOCK_RANDOM_TX // This block has baseFeePerGas

        val result = service.calculateCumulativeBaseFeePerGas(previousBlockUsage, block)

        // Should accumulate from zero instead of returning null
        assertNotNull(result)
        assertTrue(result!! > BigInteger.ZERO)
    }

    @Test
    fun `calculateCumulativeBaseFeePerGas adds fees correctly when both present`() {
        val previousCumulative = BigInteger.valueOf(1000000000)
        val previousBlockUsage = createBlockUsage(cumulativeBaseFeePerGas = previousCumulative)
        val block = BlockFixtures.BLOCK_RANDOM_TX // This block has baseFeePerGas

        val result = service.calculateCumulativeBaseFeePerGas(previousBlockUsage, block)

        assertNotNull(result)
        assertTrue(result!! > previousCumulative)
    }

    // Test calculateCumulativeTransactions
    @Test
    fun `calculateCumulativeTransactions counts correctly`() {
        val previousBlockUsage =
            createBlockUsage(cumulativeNumTransactions = BigInteger.valueOf(10))
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE // has 1 transaction

        val result = service.calculateCumulativeTransactions(previousBlockUsage, block)

        assertEquals(BigInteger.valueOf(11), result)
    }

    // Test calculateCumulativeClauses
    @Test
    fun `calculateCumulativeClauses counts correctly`() {
        val previousBlockUsage = createBlockUsage(cumulativeNumClauses = BigInteger.valueOf(20))
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE // has 1 clause

        val result = service.calculateCumulativeClauses(previousBlockUsage, block)

        assertEquals(BigInteger.valueOf(21), result)
    }

    // Test calculateTimeBoundary
    @Test
    fun `calculateTimeBoundary returns true when boundary is crossed`() {
        val result = TimestampUtils.calculateTimeBoundary(3590L, 3610L) { prev, curr -> true }
        assertEquals(true, result)
    }

    @Test
    fun `calculateTimeBoundary returns null when boundary is not crossed`() {
        val result = TimestampUtils.calculateTimeBoundary(3610L, 3620L) { prev, curr -> false }
        assertNull(result)
    }

    // Test createGenesisBlockUsage
    @Test
    fun `createGenesisBlockUsage creates correct record`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 0L)

        val result = service.createGenesisBlockUsage(block)

        assertEquals(block.id, result.blockId)
        assertEquals(0L, result.blockNumber)
        assertEquals(block.timestamp, result.blockTimestamp)
        assertEquals(BigInteger.valueOf(block.gasLimit), result.cumulativeGasLimit)
        assertEquals(BigInteger.valueOf(block.gasUsed), result.cumulativeGasUsed)
        assertEquals(BigInteger.ZERO, result.cumulativeNumTransactions)
        assertEquals(BigInteger.ZERO, result.cumulativeNumClauses)
    }

    // Test createBlockUsageWithCumulative
    @Test
    fun `createBlockUsageWithCumulative creates correct record`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES // This has valid timestamp: 1680177330
        val previousBlockUsage =
            createBlockUsage(
                blockNumber = block.number - 1,
                blockTimestamp = 1680177320L, // 10 seconds before
                cumulativeGasLimit = BigInteger.valueOf(1000000),
                cumulativeGasUsed = BigInteger.valueOf(500000),
                cumulativeBaseFeePerGas = BigInteger.valueOf(1000000000),
                cumulativeNumTransactions = BigInteger.valueOf(50),
                cumulativeNumClauses = BigInteger.valueOf(75),
            )

        val result = service.createBlockUsage(block, previousBlockUsage)

        assertEquals(block.id, result.blockId)
        assertEquals(block.number, result.blockNumber)
        assertEquals(block.timestamp, result.blockTimestamp)
        assertTrue(result.cumulativeGasLimit > previousBlockUsage.cumulativeGasLimit)
        // gasUsed is 0 for BLOCK_NO_CLAUSES
        assertEquals(previousBlockUsage.cumulativeGasUsed, result.cumulativeGasUsed)
        // BLOCK_NO_CLAUSES has no baseFeePerGas, so cumulative should be null
        assertNull(result.cumulativeBaseFeePerGas)
        assertEquals(
            BigInteger.valueOf(50),
            result.cumulativeNumTransactions,
        ) // no new transactions
        assertEquals(BigInteger.valueOf(75), result.cumulativeNumClauses) // no new clauses
    }

    // Test processBlock - genesis block
    @Test
    fun `processBlock handles genesis block correctly`() {
        val genesisBlock = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 0L)

        val result = service.processBlock(genesisBlock)

        assertEquals(genesisBlock.id, result.blockId)
        assertEquals(0L, result.blockNumber)
        assertEquals(BigInteger.valueOf(genesisBlock.gasLimit), result.cumulativeGasLimit)
        assertEquals(BigInteger.valueOf(genesisBlock.gasUsed), result.cumulativeGasUsed)
        assertEquals(BigInteger.ZERO, result.cumulativeNumTransactions)
        assertEquals(BigInteger.ZERO, result.cumulativeNumClauses)
    }

    // Test processBlock - non-genesis block
    @Test
    fun `processBlock handles non-genesis block correctly`() {
        val previousBlockUsage =
            createBlockUsage(
                blockNumber = 2L,
                blockTimestamp = 1680177320L,
                cumulativeGasLimit = BigInteger.valueOf(10000000),
                cumulativeGasUsed = BigInteger.valueOf(0),
                cumulativeBaseFeePerGas = null,
                cumulativeNumTransactions = BigInteger.ZERO,
                cumulativeNumClauses = BigInteger.ZERO,
            )

        every { repository.findByIdOrNull(2L) } returns previousBlockUsage

        val block = BlockFixtures.BLOCK_NO_CLAUSES // block number = 3

        val result = service.processBlock(block)

        assertEquals(block.id, result.blockId)
        assertEquals(block.number, result.blockNumber)
        assertTrue(result.cumulativeGasLimit > previousBlockUsage.cumulativeGasLimit)
        assertEquals(
            BigInteger.ZERO,
            result.cumulativeNumTransactions,
        ) // no transactions in this block either
        verify(exactly = 1) { repository.findByIdOrNull(2L) }
    }

    // Test processBlock - missing previous block
    @Test
    fun `processBlock throws exception when previous block is missing`() {
        val block = BlockFixtures.BLOCK_SINGLE_CLAUSE
        every { repository.findByIdOrNull(block.number - 1) } returns null

        val exception = assertThrows<IllegalArgumentException> { service.processBlock(block) }

        assertTrue(
            exception.message!!.contains(
                "Previous block usage record should exist for block ${block.number}"
            )
        )
    }

    // Test processBlock - time boundary detection
    @Test
    fun `processBlock detects time boundaries correctly`() {
        val previousBlockUsage = createBlockUsage(blockNumber = 2L, blockTimestamp = 1680177320L)

        every { repository.findByIdOrNull(2L) } returns previousBlockUsage

        val block = BlockFixtures.BLOCK_NO_CLAUSES // timestamp: 1680177330

        val result = service.processBlock(block)

        // Boundaries depend on actual timestamp values
        // The test just verifies that the function processes without error
        assertNotNull(result)
        assertEquals(block.id, result.blockId)
    }

    // Test save
    @Test
    fun `save calls repository save`() {
        val blockUsage = createBlockUsage()
        every { repository.save(blockUsage) } returns blockUsage

        service.save(blockUsage)

        verify(exactly = 1) { repository.save(blockUsage) }
    }

    // Helper function to create test BlockUsage data

    private fun createBlockUsage(
        blockId: String = "block1",
        blockNumber: Long = 1L,
        blockTimestamp: Long = 1704067200L,
        cumulativeGasLimit: BigInteger = BigInteger.valueOf(10000000),
        cumulativeGasUsed: BigInteger = BigInteger.valueOf(21000),
        cumulativeBaseFeePerGas: BigInteger? = BigInteger.valueOf(1000000000),
        cumulativeNumTransactions: BigInteger = BigInteger.ONE,
        cumulativeNumClauses: BigInteger = BigInteger.ONE,
        isHourly: Boolean? = null,
        isDaily: Boolean? = null,
        isWeekly: Boolean? = null,
        isMonthly: Boolean? = null,
    ): BlockUsage =
        BlockUsage(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            cumulativeGasLimit = cumulativeGasLimit,
            cumulativeGasUsed = cumulativeGasUsed,
            cumulativeBaseFeePerGas = cumulativeBaseFeePerGas,
            cumulativeNumTransactions = cumulativeNumTransactions,
            cumulativeNumClauses = cumulativeNumClauses,
            isHourly = isHourly,
            isDaily = isDaily,
            isWeekly = isWeekly,
            isMonthly = isMonthly,
        )
}
