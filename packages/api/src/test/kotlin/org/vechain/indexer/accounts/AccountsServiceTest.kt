package org.vechain.indexer.accounts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedReadRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedTotals
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class AccountsServiceTest {
    private val overviews: AccountOverviewReadRepository = mockk()
    private val totals: AccountTotalsReadRepository = mockk()
    private val vthoClaimedRepository: VthoClaimedReadRepository = mockk()

    private val service =
        AccountsService(overviews, totals).also {
            ReflectionTestUtils.setField(it, "vthoClaimedRepository", vthoClaimedRepository)
        }

    @Test
    fun `getTotalSeries returns raw records for short ranges`() {
        val records = listOf(series(1_000L), series(1_010L, 2L))
        every { totals.findAllInTimestampRange(1_000L, 2_000L) } returns records

        expectThat(service.getTotalSeries(1_000L, 2_000L)).isEqualTo(records)
        verify(exactly = 1) { totals.findAllInTimestampRange(1_000L, 2_000L) }
    }

    @Test
    fun `getTotalSeries includes bookend records for sampled ranges`() {
        val startBoundary = series(900L, totalAccounts = 10L)
        val sampled = listOf(series(3_600L, totalAccounts = 12L))
        val endBoundary = series(6_000L, totalAccounts = 14L)
        every { totals.findFrameInTimestampRange(TimeFrame.HOUR, 1_000L, 6_000L) } returns sampled
        every { totals.findLatestAtOrBefore(1_000L) } returns startBoundary
        every { totals.findLatestAtOrBefore(6_000L) } returns endBoundary

        val result = service.getTotalSeries(1_000L, 6_000L)

        expectThat(result.map { it.blockTimestamp }).isEqualTo(listOf(900L, 3_600L, 6_000L))
    }

    @Test
    fun `getTotalSeries uses monthly samples for very large ranges`() {
        every { totals.findFrameInTimestampRange(TimeFrame.MONTH, 0L, 40_000_000L) } returns
            emptyList()
        every { totals.findLatestAtOrBefore(any()) } returns null

        service.getTotalSeries(0L, 40_000_000L)

        verify(exactly = 1) { totals.findFrameInTimestampRange(TimeFrame.MONTH, 0L, 40_000_000L) }
    }

    @Test
    fun `getTotalSeries rejects oversized start timestamp`() {
        val exception =
            assertThrows<BadRequestException> {
                service.getTotalSeries(31_556_889_832_694_401L, 31_556_889_832_694_401L)
            }

        expectThat(exception.message)
            .isEqualTo("Invalid 'startTimestamp' timestamp: exceeds supported Unix timestamp range")
    }

    @Test
    fun `getTotalAccountsLatest returns latest total accounts value`() {
        every { totals.findLatest() } returns series(7_200L, totalAccounts = 42L)

        expectThat(service.getTotalAccountsLatest()).isEqualTo(42L)
    }

    @Test
    fun `getOverviewWithVthoEarnings sums Stargate claims over the account`() {
        val address = Address("0x3F90bf8B314c42005103B3c94505634fA680dcEe")
        val overview = overview(address.value)
        val account = HexUtils.normalise(address.value)
        every { overviews.findByAddress(address.value) } returns overview
        every { vthoClaimedRepository.findByAccount(account) } returns
            VthoClaimedTotals(BigInteger("4"), BigInteger("9"))

        val result = service.getOverviewWithVthoEarnings(address)

        expectThat(result).isEqualTo(AccountOverviewResponse.from(overview, BigInteger("13")))
        verify(exactly = 1) { vthoClaimedRepository.findByAccount(account) }
    }

    @Test
    fun `getOverviewWithVthoEarnings falls back to zero when Stargate total is missing`() {
        val address = Address("0xb3a4831cadcee1efb78028c2ba72f29f22a197e1")
        val overview = overview(address.value)
        every { overviews.findByAddress(address.value) } returns overview
        every { vthoClaimedRepository.findByAccount(address.value) } returns null

        val result = service.getOverviewWithVthoEarnings(address)

        expectThat(result).isEqualTo(AccountOverviewResponse.from(overview, BigInteger.ZERO))
    }

    @Test
    fun `getOverviewWithVthoEarnings is null for an unknown address`() {
        val address = Address("0xb3a4831cadcee1efb78028c2ba72f29f22a197e1")
        every { overviews.findByAddress(address.value) } returns null

        expectThat(service.getOverviewWithVthoEarnings(address)).isEqualTo(null)
    }

    private fun overview(address: String) =
        AccountOverview(
            address = address,
            blockId = "0xblock",
            blockNumber = 1L,
            blockTimestamp = 2L,
            firstSeen = 3L,
            lastSeen = 4L,
            vthoBlockRewards = BigInteger("5"),
            vthoPassiveGeneration = BigInteger("7"),
        )

    private fun series(
        blockTimestamp: Long,
        totalAccounts: Long = 1L,
        blockNumber: Long = blockTimestamp,
    ) = AccountTotalsSeries("0x$blockNumber", blockNumber, blockTimestamp, totalAccounts)
}
