package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.rest.CachePolicy

internal class ActionLeaderboardServiceTest {
    private val userRoundRepo = mockk<UserRoundActionSummaryRepository>()
    private val service = ActionLeaderboardService(mockk<MongoTemplate>(), userRoundRepo)

    private fun latestRoundIs(roundId: Int?) {
        every { userRoundRepo.findFirstByEntityTypeOrderByRoundIdDesc(EntityType.GLOBAL) } returns
            roundId?.let { globalSummary(it) }
    }

    private fun globalSummary(roundId: Int) =
        UserRoundActionSummary(
            version = 1,
            blockId = "0x1",
            blockNumber = 1,
            blockTimestamp = 1,
            entity = EntityType.GLOBAL.name,
            entityType = EntityType.GLOBAL,
            roundId = roundId,
            actionsRewarded = 1,
            totalRewardAmount = BigDecimal.ONE,
            totalImpact = null,
        )

    @Test
    fun `a round behind the newest one can never change again`() {
        latestRoundIs(112)

        assertEquals(CachePolicy.IMMUTABLE, service.roundLeaderboardPolicy(111))
        assertEquals(CachePolicy.IMMUTABLE, service.roundLeaderboardPolicy(1))
    }

    @Test
    fun `the newest round on record is still open`() {
        latestRoundIs(112)

        assertEquals(CachePolicy.HOURLY, service.roundLeaderboardPolicy(112))
    }

    @Test
    fun `a round the indexer has not reached yet stays fresh`() {
        latestRoundIs(112)

        assertEquals(CachePolicy.HOURLY, service.roundLeaderboardPolicy(113))
    }

    @Test
    fun `no rounds on record settles nothing`() {
        latestRoundIs(null)

        assertEquals(CachePolicy.HOURLY, service.roundLeaderboardPolicy(111))
    }
}
