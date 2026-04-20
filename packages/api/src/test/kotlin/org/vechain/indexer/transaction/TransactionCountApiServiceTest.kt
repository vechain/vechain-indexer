package org.vechain.indexer.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class TransactionCountApiServiceTest {
    private val transactionCountSummaryRepository: TransactionCountSummaryRepository = mockk()
    private val service = TransactionCountApiService(transactionCountSummaryRepository)

    @Test
    fun `getLatestCount returns the most recently indexed summary`() {
        val summary =
            TransactionCountSummary(
                blockId = "0xabc",
                blockNumber = 12_345L,
                blockTimestamp = 1_700_000_000L,
                version = 7,
                totalTransactions = BigInteger.valueOf(987_654_321L),
                totalClauses = BigInteger.valueOf(1_234_567_890L),
                totalRevertedTransactions = BigInteger.valueOf(12_345L),
                totalRevertedClauses = BigInteger.valueOf(23_456L),
            )
        every {
            transactionCountSummaryRepository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID)
        } returns summary

        expectThat(service.getLatestCount()).isEqualTo(summary)
        verify(exactly = 1) {
            transactionCountSummaryRepository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID)
        }
    }

    @Test
    fun `getLatestCount returns null when the collection is empty`() {
        every {
            transactionCountSummaryRepository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID)
        } returns null

        expectThat(service.getLatestCount()).isNull()
    }
}
