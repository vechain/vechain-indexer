package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
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
import org.vechain.indexer.rest.CachePolicy

internal class ActionLeaderboardServiceTest {
    private val repository = mockk<ActionReadRepository>()
    private val service = ActionLeaderboardService(repository)

    private val appId = AppId("0x" + "7".repeat(64))

    private fun wallet(n: Int) = "0x" + n.toString(16).padStart(40, '0')

    private fun entity(n: Int, period: ActionPeriod = AllTime, type: EntityType = EntityType.USER) =
        EntityActionSummary(
            type,
            wallet(n),
            period,
            "0x01",
            5,
            50,
            10L - n,
            BigDecimal("1.${n}0"),
            null,
        )

    @Test
    fun `a round behind the newest one can never change again`() {
        every { repository.latestRound() } returns 112

        assertEquals(CachePolicy.IMMUTABLE, service.leaderboardPolicy(Round(111)))
        assertEquals(CachePolicy.IMMUTABLE, service.leaderboardPolicy(Round(1)))
        assertEquals(CachePolicy.MINUTE, service.leaderboardPolicy(Round(112)))
        assertEquals(CachePolicy.MINUTE, service.leaderboardPolicy(Round(113)))

        every { repository.latestRound() } returns null
        assertEquals(CachePolicy.MINUTE, service.leaderboardPolicy(Round(111)))
    }

    @Test
    fun `the all-time and daily boards keep moving, so they never ask which round is open`() {
        assertEquals(CachePolicy.MINUTE, service.leaderboardPolicy(AllTime))
        assertEquals(CachePolicy.MINUTE, service.leaderboardPolicy(Day("2026-09-01")))
        verify(exactly = 0) { repository.latestRound() }
    }

    @Test
    fun `a page is one row past its size and points its cursor at the last row shown`() {
        every {
            repository.leaderboard(
                AllTime,
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                Direction.DESC,
                3,
                null,
                null,
            )
        } returns listOf(entity(1), entity(2), entity(3))

        val page = service.getUserLeaderboard(AllTime, 2, null, "actionsRewarded", null)

        assertEquals(listOf(wallet(1), wallet(2)), page.data.map { it.wallet })
        assertEquals(9, page.data.first().actionsRewarded)
        assertNull(page.data.first().roundId)
        assertEquals(true, page.pagination.hasNext)
        assertEquals("8|${wallet(2)}", page.pagination.cursor)
    }

    @Test
    fun `the cursor's sort value and entity resume the query, and the last page has none`() {
        every {
            repository.leaderboard(
                Round(4),
                EntityType.APP,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.ASC,
                21,
                "1.20",
                wallet(2),
            )
        } returns listOf(entity(3, Round(4), EntityType.APP))

        val page =
            service.getAppLeaderboard(
                Round(4),
                null,
                "asc",
                "totalRewardAmount",
                "1.20|${wallet(2)}",
            )

        assertEquals(listOf(wallet(3)), page.data.map { it.appId })
        assertEquals(4, page.data.single().roundId)
        assertEquals(false, page.pagination.hasNext)
        assertNull(page.pagination.cursor)
    }

    @Test
    fun `an app's board is its wallets, with the reward as the cursor's plain decimal`() {
        every {
            repository.appLeaderboard(
                Day("2026-09-01"),
                appId.value,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.DESC,
                2,
                null,
                null,
            )
        } returns
            listOf(
                AppUserActionSummary(
                    appId.value,
                    wallet(1),
                    Day("2026-09-01"),
                    "0x01",
                    5,
                    50,
                    3,
                    BigDecimal("2.50"),
                    null,
                ),
                AppUserActionSummary(
                    appId.value,
                    wallet(2),
                    Day("2026-09-01"),
                    "0x01",
                    5,
                    50,
                    1,
                    BigDecimal("0.5"),
                    null,
                ),
            )

        val page =
            service.getUserAppLeaderboard(
                appId,
                Day("2026-09-01"),
                1,
                null,
                "totalRewardAmount",
                null,
            )

        assertEquals(wallet(1), page.data.single().user)
        assertEquals(appId.value, page.data.single().appId)
        assertEquals("2026-09-01", page.data.single().date)
        assertEquals(2.5, page.data.single().totalRewardAmount)
        assertEquals("2.50|${wallet(1)}", page.pagination.cursor)
    }

    @Test
    fun `a sort field the boards do not have is a bad request`() {
        assertThrows(BadRequestException::class.java) {
            service.getUserLeaderboard(AllTime, null, null, "roundId", null)
        }
    }
}
