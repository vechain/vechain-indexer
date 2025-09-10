package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.thor.Address

@ExtendWith(MockKExtension::class)
internal class ActionServiceTest {
    @MockK lateinit var userAllTimeRepo: UserAllTimeActionSummaryRepository
    @MockK lateinit var userDailyRepo: UserDailyActionSummaryRepository
    @MockK lateinit var userRoundRepo: UserRoundActionSummaryRepository
    @MockK lateinit var appAllTimeRepo: AppAllTimeActionSummaryRepository
    @MockK lateinit var appDailyRepo: AppDailyActionSummaryRepository
    @MockK lateinit var appRoundRepo: AppRoundActionSummaryRepository

    private lateinit var service: ActionService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service =
            ActionService(
                userAllTimeRepo,
                userDailyRepo,
                userRoundRepo,
                appAllTimeRepo,
                appDailyRepo,
                appRoundRepo,
            )
    }

    @Test
    fun `getAllTimeWalletOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val normalized = "0xdef"

        every { userAllTimeRepo.findByEntity(normalized) } returns null
        every { appAllTimeRepo.findAppIdsByUser(normalized) } returns emptyList()

        val result = service.getAllTimeWalletOverview(wallet)
        assertEquals(normalized, result.wallet)
        assertEquals(0.0, result.totalRewardAmount)
        assertEquals(0, result.actionsRewarded)
        assertEquals(null, result.totalImpact)
        assertEquals(null, result.rankByReward)
        assertEquals(null, result.rankByActionsRewarded)
        assertEquals(emptyList<String>(), result.uniqueXAppInteractions)
        assertEquals(null, result.roundId)
        assertEquals(null, result.date)
    }

    @Test
    fun `getAllTimeWalletOverview returns correct overview when data exists`() {
        val wallet = Address("0xabc")
        val normalized = "0xabc"
        val impact =
            mockk<Impact>(relaxed = true) // Replace Any with actual Impact type if available
        val overview = mockk<UserAllTimeActionSummary>(relaxed = true)

        every { userAllTimeRepo.findByEntity(normalized) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(100)
        every { overview.actionsRewarded } returns 5
        every { overview.totalImpact } returns impact
        every {
            userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                BigDecimal(100),
                EntityType.USER,
            )
        } returns 9
        every {
            userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(5, EntityType.USER)
        } returns 4

        val app1 = mockk<AppAllTimeActionSummary> { every { this@mockk.appId } returns "app1" }
        val app2 = mockk<AppAllTimeActionSummary> { every { this@mockk.appId } returns "app2" }

        every { appAllTimeRepo.findAppIdsByUser(normalized) } returns listOf(app1, app2)

        val result = service.getAllTimeWalletOverview(wallet)

        assertEquals(normalized, result.wallet)
        assertEquals(100.0, result.totalRewardAmount)
        assertEquals(5, result.actionsRewarded)
        assertEquals(impact, result.totalImpact)
        assertEquals(10, result.rankByReward)
        assertEquals(5, result.rankByActionsRewarded)
        assertEquals(listOf("app1", "app2"), result.uniqueXAppInteractions)
        assertEquals(null, result.roundId)
        assertEquals(null, result.date)
    }

    @Test
    fun `getDailyWalletOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val date = "2023-10-10"
        val normalized = "0xdef"

        every { userDailyRepo.findByEntityAndDate(normalized, date) } returns null
        every { appDailyRepo.findAppIdsByUserAndDate(normalized, date) } returns emptyList()

        val result = service.getDailyWalletOverview(wallet, date)

        assertEquals(normalized, result.wallet)

        assertEquals(0.0, result.totalRewardAmount)
        assertEquals(0, result.actionsRewarded)
        assertEquals(null, result.totalImpact)
        assertEquals(null, result.rankByReward)
        assertEquals(null, result.rankByActionsRewarded)
        assertEquals(emptyList<String>(), result.uniqueXAppInteractions)
        assertEquals(null, result.roundId)
        assertEquals(date, result.date)
    }

    @Test
    fun `getDailyWalletOverview returns correct overview when data exists`() {
        val wallet = Address("0xabc")
        val date = "2023-10-10"
        val normalized = "0xabc"
        val impact =
            mockk<Impact>(relaxed = true) // Replace Any with actual Impact type if available
        val overview = mockk<UserDailyActionSummary>(relaxed = true)

        every { userDailyRepo.findByEntityAndDate(normalized, date) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(50)
        every { overview.actionsRewarded } returns 3
        every { overview.totalImpact } returns impact
        every {
            userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                BigDecimal(50),
                EntityType.USER,
                date,
            )
        } returns 19
        every {
            userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                3,
                EntityType.USER,
                date,
            )
        } returns 9

        val app1 = mockk<AppDailyActionSummary> { every { this@mockk.appId } returns "app1" }
        val app2 = mockk<AppDailyActionSummary> { every { this@mockk.appId } returns "app2" }

        every { appDailyRepo.findAppIdsByUserAndDate(normalized, date) } returns listOf(app1, app2)

        val result = service.getDailyWalletOverview(wallet, date)

        assertEquals(normalized, result.wallet)
        assertEquals(50.0, result.totalRewardAmount)
        assertEquals(3, result.actionsRewarded)
        assertEquals(impact, result.totalImpact)
        assertEquals(20, result.rankByReward)
        assertEquals(10, result.rankByActionsRewarded)
        assertEquals(listOf("app1", "app2"), result.uniqueXAppInteractions)
        assertEquals(null, result.roundId)
        assertEquals(date, result.date)
    }

    @Test
    fun `getRoundWalletOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val roundId = 1
        val normalized = "0xdef"

        every { userRoundRepo.findByEntityAndRoundId(normalized, roundId) } returns null
        every { appRoundRepo.findAppIdsByUserAndRoundId(normalized, roundId) } returns emptyList()

        val result = service.getRoundWalletOverview(wallet, roundId)

        assertEquals(normalized, result.wallet)
        assertEquals(0.0, result.totalRewardAmount)
        assertEquals(0, result.actionsRewarded)
        assertEquals(null, result.totalImpact)
        assertEquals(null, result.rankByReward)
        assertEquals(null, result.rankByActionsRewarded)
        assertEquals(emptyList<String>(), result.uniqueXAppInteractions)
        assertEquals(roundId, result.roundId)
    }

    @Test
    fun `getRoundWalletOverview returns correct overview when data exists`() {
        val wallet = Address("0xabc")
        val roundId = 1
        val normalized = "0xabc"
        val impact = mockk<Impact>(relaxed = true)
        val overview = mockk<UserRoundActionSummary>(relaxed = true)

        every { userRoundRepo.findByEntityAndRoundId(normalized, roundId) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(75)
        every { overview.actionsRewarded } returns 7
        every { overview.totalImpact } returns impact
        every {
            userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                BigDecimal(75),
                EntityType.USER,
                roundId,
            )
        } returns 14
        every {
            userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                7,
                EntityType.USER,
                roundId,
            )
        } returns 6

        val app1 = mockk<AppRoundActionSummary> { every { this@mockk.appId } returns "app1" }
        val app2 = mockk<AppRoundActionSummary> { every { this@mockk.appId } returns "app2" }

        every { appRoundRepo.findAppIdsByUserAndRoundId(normalized, roundId) } returns
            listOf(app1, app2)

        val result = service.getRoundWalletOverview(wallet, roundId)

        assertEquals(normalized, result.wallet)
        assertEquals(75.0, result.totalRewardAmount)
        assertEquals(7, result.actionsRewarded)
        assertEquals(impact, result.totalImpact)
        assertEquals(15, result.rankByReward)
        assertEquals(7, result.rankByActionsRewarded)
        assertEquals(listOf("app1", "app2"), result.uniqueXAppInteractions)
        assertEquals(roundId, result.roundId)
    }
}
