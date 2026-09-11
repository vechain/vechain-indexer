package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.vechain.indexer.blocks.BlocksReadRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class TransactionCountApiServiceTest {
    private val repository: BlocksReadRepository = mockk()
    private val service = TransactionCountApiService(repository)

    @Test
    fun `getLatestCount returns the newest block's running totals`() {
        val summary =
            TransactionCountSummary(
                blockId = "0xabc",
                blockNumber = 12_345L,
                blockTimestamp = 1_700_000_000L,
                totalTransactions = BigInteger.valueOf(987_654_321L),
                totalClauses = BigInteger.valueOf(1_234_567_890L),
                totalRevertedTransactions = BigInteger.valueOf(12_345L),
                totalRevertedClauses = BigInteger.valueOf(23_456L),
            )
        every { repository.latestTotals() } returns summary

        expectThat(service.getLatestCount()).isEqualTo(summary)
    }

    @Test
    fun `getLatestCount returns null before the first block is indexed`() {
        every { repository.latestTotals() } returns null

        expectThat(service.getLatestCount()).isNull()
    }
}
