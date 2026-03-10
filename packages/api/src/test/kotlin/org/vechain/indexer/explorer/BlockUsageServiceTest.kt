package org.vechain.indexer.explorer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class BlockUsageServiceTest {
    private val repository: BlockUsageRepository = mockk()
    private val service = BlockUsageService(repository)

    @Test
    fun `getBlockUsage returns raw blocks for short ranges`() {
        val records =
            listOf(blockUsage(blockTimestamp = 1_000L), blockUsage(blockTimestamp = 1_010L))
        every { repository.findAllInTimestampRange(1_000L, 2_000L) } returns records

        val result = service.getBlockUsage(1_000L, 2_000L)

        expectThat(result).isEqualTo(records)
        verify(exactly = 1) { repository.findAllInTimestampRange(1_000L, 2_000L) }
    }

    @Test
    fun `getBlockUsage includes bookend records for sampled ranges`() {
        val startBoundary = blockUsage(blockNumber = 90L, blockTimestamp = 900L)
        val sampled = listOf(blockUsage(blockNumber = 360L, blockTimestamp = 3_600L))
        val endBoundary = blockUsage(blockNumber = 600L, blockTimestamp = 6_000L)

        every { repository.findHourlyInTimestampRange(1_000L, 6_000L) } returns sampled
        every {
            repository.findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc(1_000L)
        } returns startBoundary
        every {
            repository.findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc(6_000L)
        } returns endBoundary

        val result = service.getBlockUsage(1_000L, 6_000L)

        expectThat(result).hasSize(3)
        expectThat(result.map { it.blockTimestamp }).isEqualTo(listOf(900L, 3_600L, 6_000L))
    }

    @Test
    fun `getBlockUsage uses monthly samples for very large ranges`() {
        every { repository.findMonthlyInTimestampRange(0L, 40_000_000L) } returns emptyList()
        every {
            repository.findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc(any())
        } returns null

        service.getBlockUsage(0L, 40_000_000L)

        verify(exactly = 1) { repository.findMonthlyInTimestampRange(0L, 40_000_000L) }
    }

    private fun blockUsage(blockNumber: Long = 1L, blockTimestamp: Long) =
        BlockUsage(
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            cumulativeGasLimit = java.math.BigInteger.ONE,
            cumulativeGasUsed = java.math.BigInteger.ONE,
            cumulativeBaseFeePerGas = java.math.BigInteger.ONE,
            cumulativeNumTransactions = java.math.BigInteger.ONE,
            cumulativeNumClauses = java.math.BigInteger.ONE,
            isHourly = null,
            isDaily = null,
            isWeekly = null,
            isMonthly = null,
        )
}
