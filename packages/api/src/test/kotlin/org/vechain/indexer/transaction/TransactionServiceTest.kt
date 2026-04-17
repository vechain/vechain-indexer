package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class TransactionServiceTest {
    private val transactionRepository: TransactionRepository = mockk()
    private val transactionCountSummaryRepository: TransactionCountSummaryRepository = mockk()
    private val service =
        TransactionService(transactionRepository, transactionCountSummaryRepository)

    @Test
    fun `getLatestCount returns the most recently indexed summary`() {
        val summary =
            TransactionCountSummary(
                blockId = "0xabc",
                blockNumber = 12_345L,
                blockTimestamp = 1_700_000_000L,
                totalTransactions = BigInteger.valueOf(987_654_321L),
                totalClauses = BigInteger.valueOf(1_234_567_890L),
            )
        every { transactionCountSummaryRepository.findFirstByOrderByBlockNumberDesc() } returns
            summary

        expectThat(service.getLatestCount()).isEqualTo(summary)
        verify(exactly = 1) {
            transactionCountSummaryRepository.findFirstByOrderByBlockNumberDesc()
        }
    }

    @Test
    fun `getLatestCount returns null when the collection is empty`() {
        every { transactionCountSummaryRepository.findFirstByOrderByBlockNumberDesc() } returns null

        expectThat(service.getLatestCount()).isNull()
    }
}
