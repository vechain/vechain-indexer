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
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.thor.Address

@ExtendWith(MockKExtension::class)
internal class ActionServiceTest {
    @MockK lateinit var historyRepo: HistoryRepository
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
                historyRepo,
                userAllTimeRepo,
                userDailyRepo,
                userRoundRepo,
                appAllTimeRepo,
                appDailyRepo,
                appRoundRepo,
            )
    }

    @Test
    fun `getAllTimeUserOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val normalized = "0xdef"

        every { userAllTimeRepo.findByEntity(normalized) } returns null
        every { appAllTimeRepo.findAppIdsByUser(normalized) } returns emptyList()

        val result = service.getAllTimeUserOverview(wallet)
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
    fun `getAllTimeUserOverview returns correct overview when data exists`() {
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

        val result = service.getAllTimeUserOverview(wallet)

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
    fun `getDailyUserOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val date = "2023-10-10"
        val normalized = "0xdef"

        every { userDailyRepo.findByEntityAndDate(normalized, date) } returns null
        every { appDailyRepo.findByUserAndDate(normalized, date) } returns emptyList()

        val result = service.getDailyUserOverview(wallet, date)

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
    fun `getDailyUserOverview returns correct overview when data exists`() {
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

        every { appDailyRepo.findByUserAndDate(normalized, date) } returns listOf(app1, app2)

        val result = service.getDailyUserOverview(wallet, date)

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
    fun `getRoundUserOverview returns default overview when no data exists`() {
        val wallet = Address("0xdef")
        val roundId = 1
        val normalized = "0xdef"

        every { userRoundRepo.findByEntityAndRoundId(normalized, roundId) } returns null
        every { appRoundRepo.findAppIdsByUserAndRoundId(normalized, roundId) } returns emptyList()

        val result = service.getRoundUserOverview(wallet, roundId)

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
    fun `getRoundUserOverview returns correct overview when data exists`() {
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

        val result = service.getRoundUserOverview(wallet, roundId)

        assertEquals(normalized, result.wallet)
        assertEquals(75.0, result.totalRewardAmount)
        assertEquals(7, result.actionsRewarded)
        assertEquals(impact, result.totalImpact)
        assertEquals(15, result.rankByReward)
        assertEquals(7, result.rankByActionsRewarded)
        assertEquals(listOf("app1", "app2"), result.uniqueXAppInteractions)
        assertEquals(roundId, result.roundId)
    }

    // App Overview Tests

    @Test
    fun `getAppAllTimeOverview returns precomputed totalUniqueUserInteractions`() {
        val appId = AppId("app-1")
        val overview = mockk<UserAllTimeActionSummary>(relaxed = true)

        every { userAllTimeRepo.findByEntity(appId.value) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(200)
        every { overview.actionsRewarded } returns 10
        every { overview.totalImpact } returns null
        every { overview.totalUniqueUserInteractions } returns 42
        every {
            userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                BigDecimal(200),
                EntityType.APP,
            )
        } returns 2
        every {
            userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(10, EntityType.APP)
        } returns 1

        val result = service.getAppAllTimeOverview(appId)

        assertEquals(appId.value, result.appId)
        assertEquals(42, result.totalUniqueUserInteractions)
        assertEquals(200.0, result.totalRewardAmount)
        assertEquals(10, result.actionsRewarded)
    }

    @Test
    fun `getAppAllTimeOverview returns 0 when no data exists`() {
        val appId = AppId("app-1")

        every { userAllTimeRepo.findByEntity(appId.value) } returns null

        val result = service.getAppAllTimeOverview(appId)

        assertEquals(0, result.totalUniqueUserInteractions)
        assertEquals(0.0, result.totalRewardAmount)
    }

    // Global Overview Tests

    @Test
    fun `getGlobalAllTimeOverview returns precomputed totalUniqueUserInteractions`() {
        val overview = mockk<UserAllTimeActionSummary>(relaxed = true)

        every { userAllTimeRepo.findByEntity(EntityType.GLOBAL.name) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(500)
        every { overview.actionsRewarded } returns 100
        every { overview.totalImpact } returns null
        every { overview.totalUniqueUserInteractions } returns 99

        val result = service.getGlobalAllTimeOverview()

        assertEquals(99, result.totalUniqueUserInteractions)
        assertEquals(500.0, result.totalRewardAmount)
        assertEquals(100, result.actionsRewarded)
    }

    @Test
    fun `getGlobalAllTimeOverview returns 0 when no data exists`() {
        every { userAllTimeRepo.findByEntity(EntityType.GLOBAL.name) } returns null

        val result = service.getGlobalAllTimeOverview()

        assertEquals(0, result.totalUniqueUserInteractions)
        assertEquals(0.0, result.totalRewardAmount)
    }

    @Test
    fun `getGlobalDailyOverview returns precomputed totalUniqueUserInteractions`() {
        val date = "2023-10-10"
        val overview = mockk<UserDailyActionSummary>(relaxed = true)

        every { userDailyRepo.findByEntityAndDate(EntityType.GLOBAL.name, date) } returns overview
        every { overview.totalRewardAmount } returns BigDecimal(100)
        every { overview.actionsRewarded } returns 20
        every { overview.totalImpact } returns null
        every { overview.totalUniqueUserInteractions } returns 15

        val result = service.getGlobalDailyOverview(date)

        assertEquals(15, result.totalUniqueUserInteractions)
        assertEquals(date, result.date)
    }

    @Test
    fun `getGlobalRoundOverview returns precomputed totalUniqueUserInteractions`() {
        val roundId = 5
        val overview = mockk<UserRoundActionSummary>(relaxed = true)

        every { userRoundRepo.findByEntityAndRoundId(EntityType.GLOBAL.name, roundId) } returns
            overview
        every { overview.totalRewardAmount } returns BigDecimal(300)
        every { overview.actionsRewarded } returns 50
        every { overview.totalImpact } returns null
        every { overview.totalUniqueUserInteractions } returns 30

        val result = service.getGlobalRoundOverview(roundId)

        assertEquals(30, result.totalUniqueUserInteractions)
        assertEquals(roundId, result.roundId)
    }
}
