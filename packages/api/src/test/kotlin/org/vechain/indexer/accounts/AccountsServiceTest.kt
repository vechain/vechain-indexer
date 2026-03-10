package org.vechain.indexer.accounts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.vechain.indexer.accounts.AccountTotalsSeriesRecordType.SERIES
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class AccountsServiceTest {
    private val totalAccountsRepository: TotalAccountsRepository = mockk()
    private val accountOverviewRepository: AccountOverviewRepository = mockk()
    private val accountTotalsSeriesRepository: AccountTotalsSeriesRepository = mockk()

    private val service =
        AccountsService(
            totalAccountsRepository,
            accountOverviewRepository,
            accountTotalsSeriesRepository,
        )

    @Test
    fun `getTotalSeries returns raw records for short ranges`() {
        val records =
            listOf(accountTotalsSeries(blockTimestamp = 1_000L), accountTotalsSeries(1_010L, 2L))
        every { accountTotalsSeriesRepository.findAllInTimestampRange(1_000L, 2_000L) } returns
            records

        val result = service.getTotalSeries(1_000L, 2_000L)

        expectThat(result).isEqualTo(records)
        verify(exactly = 1) {
            accountTotalsSeriesRepository.findAllInTimestampRange(1_000L, 2_000L)
        }
    }

    @Test
    fun `getTotalSeries includes bookend records for sampled ranges`() {
        val startBoundary = accountTotalsSeries(blockTimestamp = 900L, totalAccounts = 10L)
        val sampled = listOf(accountTotalsSeries(blockTimestamp = 3_600L, totalAccounts = 12L))
        val endBoundary = accountTotalsSeries(blockTimestamp = 6_000L, totalAccounts = 14L)

        every { accountTotalsSeriesRepository.findHourlyInTimestampRange(1_000L, 6_000L) } returns
            sampled
        every {
            accountTotalsSeriesRepository
                .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    SERIES,
                    1_000L,
                )
        } returns startBoundary
        every {
            accountTotalsSeriesRepository
                .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    SERIES,
                    6_000L,
                )
        } returns endBoundary

        val result = service.getTotalSeries(1_000L, 6_000L)

        expectThat(result).hasSize(3)
        expectThat(result.map { it.blockTimestamp }).isEqualTo(listOf(900L, 3_600L, 6_000L))
    }

    @Test
    fun `getTotalSeries uses monthly samples for very large ranges`() {
        every { accountTotalsSeriesRepository.findMonthlyInTimestampRange(0L, 40_000_000L) } returns
            emptyList()
        every {
            accountTotalsSeriesRepository
                .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                    SERIES,
                    any(),
                )
        } returns null

        service.getTotalSeries(0L, 40_000_000L)

        verify(exactly = 1) {
            accountTotalsSeriesRepository.findMonthlyInTimestampRange(0L, 40_000_000L)
        }
    }

    private fun accountTotalsSeries(
        blockTimestamp: Long,
        totalAccounts: Long = 1L,
        blockNumber: Long = blockTimestamp,
    ) =
        AccountTotalsSeries(
            id = "series-$blockNumber",
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            recordType = SERIES,
            totalAccounts = totalAccounts,
            address = null,
            isHourly = null,
            isDaily = null,
            isWeekly = null,
            isMonthly = null,
        )
}
