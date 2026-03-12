package org.vechain.indexer.accounts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Test
import org.vechain.indexer.accounts.AccountTotalsSeriesRecordType.SERIES
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccount
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.IdUtils
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class AccountsServiceTest {
    private val totalAccountsRepository: TotalAccountsRepository = mockk()
    private val accountOverviewRepository: AccountOverviewRepository = mockk()
    private val accountTotalsSeriesRepository: AccountTotalsSeriesRepository = mockk()
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository = mockk()

    private val service =
        AccountsService(
            totalAccountsRepository,
            accountOverviewRepository,
            accountTotalsSeriesRepository,
            vthoClaimedByAccountRepository,
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

    @Test
    fun `getOverviewWithVthoEarnings loads Stargate totals by generated id`() {
        val address = Address("0x3F90bf8B314c42005103B3c94505634fA680dcEe")
        val overview =
            AccountOverview(
                address = address.value,
                blockId = "0xblock",
                blockNumber = 1L,
                blockTimestamp = 2L,
                version = 1,
                firstSeen = 3L,
                lastSeen = 4L,
                vthoBlockRewards = BigInteger("5"),
                vthoPassiveGeneration = BigInteger("7"),
            )
        val stargateClaimed =
            VthoClaimedByAccount(
                version = 1,
                blockId = "0xclaim",
                blockNumber = 10L,
                blockTimestamp = 11L,
                total = BigInteger("13"),
                legacyRewards = BigInteger.ZERO,
                delegationRewards = BigInteger("13"),
                account = HexUtils.normalise(address.value),
                tokenId = null,
            )
        val expectedId = IdUtils.generateId(HexUtils.normalise(address.value))

        every { accountOverviewRepository.findById(address.value) } returns Optional.of(overview)
        every { vthoClaimedByAccountRepository.findById(expectedId) } returns
            Optional.of(stargateClaimed)

        val result = service.getOverviewWithVthoEarnings(address)

        expectThat(result).isEqualTo(AccountOverviewResponse.from(overview, BigInteger("13")))
        verify(exactly = 1) { accountOverviewRepository.findById(address.value) }
        verify(exactly = 1) { vthoClaimedByAccountRepository.findById(expectedId) }
    }

    @Test
    fun `getOverviewWithVthoEarnings falls back to zero when Stargate total is missing`() {
        val address = Address("0xb3a4831cadcee1efb78028c2ba72f29f22a197e1")
        val overview =
            AccountOverview(
                address = address.value,
                blockId = "0xblock",
                blockNumber = 1L,
                blockTimestamp = 2L,
                version = 1,
                firstSeen = 3L,
                lastSeen = 4L,
                vthoBlockRewards = BigInteger("5"),
                vthoPassiveGeneration = BigInteger("7"),
            )
        val expectedId = IdUtils.generateId(HexUtils.normalise(address.value))

        every { accountOverviewRepository.findById(address.value) } returns Optional.of(overview)
        every { vthoClaimedByAccountRepository.findById(expectedId) } returns Optional.empty()

        val result = service.getOverviewWithVthoEarnings(address)

        expectThat(result).isEqualTo(AccountOverviewResponse.from(overview, BigInteger.ZERO))
        verify(exactly = 1) { vthoClaimedByAccountRepository.findById(expectedId) }
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
