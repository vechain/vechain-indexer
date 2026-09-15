package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.ActionPeriod.AllTime
import org.vechain.indexer.b3tr.action.ActionPeriod.Day
import org.vechain.indexer.b3tr.action.ActionPeriod.Round
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryReadRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.Address

internal class ActionServiceTest {
    private val historyRepo: HistoryReadRepository = mockk()
    private val repository: ActionReadRepository = mockk()
    private val service =
        ActionService(historyRepo, repository, Dispatchers.IO.limitedParallelism(4))

    private val wallet = "0x" + "a".repeat(40)
    private val appId = AppId("0x" + "7".repeat(64))
    private val impact = Impact(carbon = 12)

    private fun entity(
        type: EntityType,
        entity: String,
        period: ActionPeriod,
        actions: Long = 3,
        amount: String = "7.5",
        uniqueUsers: Long = 0,
    ) =
        EntityActionSummary(
            type,
            entity,
            period,
            "0x01",
            5,
            50,
            actions,
            BigDecimal(amount),
            impact,
            uniqueUsers,
        )

    @Test
    fun `user actions are the B3TR_ACTION rows to the wallet, paged one row past the page`() {
        val row =
            IndexedHistoryEvent(
                id = "1",
                blockId = "0x01",
                blockNumber = 5,
                blockTimestamp = 50,
                txId = "0x02",
                eventName = HistoryEventName.B3TR_ACTION,
                appId = appId.value,
                from = "0x" + "d".repeat(40),
                to = wallet,
                value = "5000000000000000000",
            )
        every { historyRepo.findActions(wallet, null, 10L, null, 0L, 2, Direction.DESC) } returns
            listOf(row, row)

        val page = service.getUserActions(Address(wallet.uppercase()), 10L, null, 0, 1, null)

        assertEquals(1, page.data.size)
        assertEquals(0, BigDecimal("5").compareTo(page.data.single().amount))
        assertEquals(true, page.pagination.hasNext)
    }

    @Test
    fun `a user overview ranks the wallet one past those ahead and lists its apps`() {
        val period = Round(4)
        every { repository.findEntity(period, EntityType.USER, wallet) } returns
            entity(EntityType.USER, wallet, period)
        every {
            repository.countEntitiesAbove(
                period,
                EntityType.USER,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                BigDecimal("7.5"),
            )
        } returns 9
        every {
            repository.countEntitiesAbove(
                period,
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal(3),
            )
        } returns 1
        every { repository.findAppIds(period, wallet) } returns listOf(appId.value)

        val overview = service.getUserOverview(Address(wallet.uppercase()), period)

        assertEquals(wallet, overview.wallet)
        assertEquals(4, overview.roundId)
        assertNull(overview.date)
        assertEquals(7.5, overview.totalRewardAmount)
        assertEquals(3, overview.actionsRewarded)
        assertEquals(impact, overview.totalImpact)
        assertEquals(10, overview.rankByReward)
        assertEquals(2, overview.rankByActionsRewarded)
        assertEquals(listOf(appId.value), overview.uniqueXAppInteractions)
    }

    @Test
    fun `a wallet without a row is unranked but still shown the apps it used`() {
        every { repository.findEntity(Day("2026-09-01"), EntityType.USER, wallet) } returns null
        every { repository.findAppIds(Day("2026-09-01"), wallet) } returns emptyList()

        val overview = service.getUserOverview(Address(wallet), Day("2026-09-01"))

        assertEquals("2026-09-01", overview.date)
        assertEquals(0.0, overview.totalRewardAmount)
        assertEquals(0, overview.actionsRewarded)
        assertNull(overview.rankByReward)
        assertNull(overview.rankByActionsRewarded)
        assertEquals(emptyList<String>(), overview.uniqueXAppInteractions)
        verify(exactly = 0) { repository.countEntitiesAbove(any(), any(), any(), any()) }
    }

    @Test
    fun `a user's overview on an app ranks it among the app's wallets`() {
        every { repository.findAppUser(AllTime, appId.value, wallet) } returns
            AppUserActionSummary(
                appId.value,
                wallet,
                AllTime,
                "0x01",
                5,
                50,
                2,
                BigDecimal("1.25"),
                null,
            )
        every {
            repository.countAppUsersAbove(
                AllTime,
                appId.value,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                BigDecimal("1.25"),
            )
        } returns 0
        every {
            repository.countAppUsersAbove(
                AllTime,
                appId.value,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal(2),
            )
        } returns 4

        val overview = service.getUserAppOverview(Address(wallet), appId, AllTime)

        assertEquals(appId.value, overview.appId)
        assertNull(overview.roundId)
        assertEquals(1.25, overview.totalRewardAmount)
        assertEquals(1, overview.rankByReward)
        assertEquals(5, overview.rankByActionsRewarded)

        every { repository.findAppUser(AllTime, appId.value, wallet) } returns null
        assertNull(service.getUserAppOverview(Address(wallet), appId, AllTime).rankByReward)
    }

    @Test
    fun `an app overview carries the wallets it has rewarded and its rank among apps`() {
        every { repository.findEntity(AllTime, EntityType.APP, appId.value) } returns
            entity(EntityType.APP, appId.value, AllTime, uniqueUsers = 321)
        every {
            repository.countEntitiesAbove(
                AllTime,
                EntityType.APP,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                BigDecimal("7.5"),
            )
        } returns 2
        every {
            repository.countEntitiesAbove(
                AllTime,
                EntityType.APP,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal(3),
            )
        } returns 0

        val overview = service.getAppOverview(appId, AllTime)

        assertEquals(321, overview.totalUniqueUserInteractions)
        assertEquals(3, overview.rankByReward)
        assertEquals(1, overview.rankByActionsRewarded)

        every { repository.findEntity(AllTime, EntityType.APP, appId.value) } returns null
        assertEquals(0, service.getAppOverview(appId, AllTime).totalUniqueUserInteractions)
    }

    @Test
    fun `the global overview is the GLOBAL row of the period`() {
        every { repository.findEntity(Day("2026-09-01"), EntityType.GLOBAL, "GLOBAL") } returns
            entity(
                EntityType.GLOBAL,
                "GLOBAL",
                Day("2026-09-01"),
                actions = 40,
                amount = "99",
                uniqueUsers = 17,
            )

        val overview = service.getGlobalOverview(Day("2026-09-01"))

        assertEquals("2026-09-01", overview.date)
        assertNull(overview.roundId)
        assertEquals(99.0, overview.totalRewardAmount)
        assertEquals(40, overview.actionsRewarded)
        assertEquals(17, overview.totalUniqueUserInteractions)
        assertEquals(impact, overview.totalImpact)
    }

    @Test
    fun `daily summaries are the wallet's days in the range, one row past the page`() {
        every {
            repository.findDailyRange(wallet, "2026-08-01", "2026-09-01", 0, 2, Direction.DESC)
        } returns
            listOf(
                entity(EntityType.USER, wallet, Day("2026-09-01"), amount = "1.5"),
                entity(EntityType.USER, wallet, Day("2026-08-31")),
            )

        val page =
            service.getDailySummariesForRange(
                Address(wallet),
                "2026-08-01",
                "2026-09-01",
                0,
                1,
                null,
            )

        assertEquals(1, page.data.size)
        assertEquals("2026-09-01", page.data.single().date)
        assertEquals(wallet, page.data.single().entity)
        assertEquals(BigDecimal("1.5"), page.data.single().totalRewardAmount)
        assertEquals(true, page.pagination.hasNext)

        assertThrows(BadRequestException::class.java) {
            service.getDailySummariesForRange(
                Address(wallet),
                "2026-09-02",
                "2026-09-01",
                0,
                1,
                null,
            )
        }
    }

    @Test
    fun `a request may name a round or a date but not both`() {
        assertEquals(Round(3), requestedPeriod(3, null))
        assertEquals(Day("2026-09-01"), requestedPeriod(null, "2026-09-01"))
        assertEquals(AllTime, requestedPeriod(null, null))
        assertThrows(BadRequestException::class.java) { requestedPeriod(3, "2026-09-01") }
    }
}
