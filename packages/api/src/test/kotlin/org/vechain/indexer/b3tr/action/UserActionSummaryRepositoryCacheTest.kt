package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType

@SpringBootApplication open class ActionRepositoryCacheTestApplication

@Configuration
@EnableCaching
open class ActionRepositoryCacheTestConfig {
    @Bean
    open fun cacheManager(): CacheManager =
        ConcurrentMapCacheManager(
            "user_all_time_action_countByTotalRewardAmountGreaterThanAndEntityType",
            "user_daily_action_countByTotalRewardAmountGreaterThanAndEntityTypeAndDate",
            "user_round_countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId",
        )
}

@DataMongoTest
@ActiveProfiles(
    "test",
    "b3tr",
    "b3tr-actions",
    "b3tr-user-all-time-action-summary",
    "b3tr-user-daily-action-summary",
    "b3tr-user-round-action-summary",
)
@ContextConfiguration(
    classes = [ActionRepositoryCacheTestApplication::class, ActionRepositoryCacheTestConfig::class]
)
internal class UserActionSummaryRepositoryCacheTest {

    @Autowired private lateinit var template: MongoTemplate
    @Autowired private lateinit var userAllTimeRepo: UserAllTimeActionSummaryRepository
    @Autowired private lateinit var userDailyRepo: UserDailyActionSummaryRepository
    @Autowired private lateinit var userRoundRepo: UserRoundActionSummaryRepository

    @BeforeEach
    fun setUp() {
        template.dropCollection(UserAllTimeActionSummary::class.java)
        template.dropCollection(UserDailyActionSummary::class.java)
        template.dropCollection(UserRoundActionSummary::class.java)
    }

    @Test
    fun `all time reward rank query accepts decimal thresholds for app entities`() {
        template.insert(
            UserAllTimeActionSummary(
                version = 1,
                blockId = "0x1",
                blockNumber = 1,
                blockTimestamp = 1,
                entity = "app-a",
                entityType = EntityType.APP,
                actionsRewarded = 5,
                totalRewardAmount = BigDecimal("1136571.207515180010875330"),
                totalImpact = null,
            )
        )
        template.insert(
            UserAllTimeActionSummary(
                version = 1,
                blockId = "0x2",
                blockNumber = 2,
                blockTimestamp = 2,
                entity = "app-b",
                entityType = EntityType.APP,
                actionsRewarded = 8,
                totalRewardAmount = BigDecimal("1136572.207515180010875330"),
                totalImpact = null,
            )
        )
        template.insert(
            UserAllTimeActionSummary(
                version = 1,
                blockId = "0x3",
                blockNumber = 3,
                blockTimestamp = 3,
                entity = "user-a",
                entityType = EntityType.USER,
                actionsRewarded = 9,
                totalRewardAmount = BigDecimal("9999999.99"),
                totalImpact = null,
            )
        )

        val threshold = BigDecimal("1136571.207515180010875330")

        assertEquals(
            1L,
            userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                threshold,
                EntityType.APP,
            ),
        )
        assertEquals(
            1L,
            userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                threshold,
                EntityType.APP,
            ),
        )
    }

    @Test
    fun `daily reward rank query accepts decimal thresholds for app entities`() {
        val date = "2026-03-30"
        template.insert(
            UserDailyActionSummary(
                version = 1,
                blockId = "0x1",
                blockNumber = 1,
                blockTimestamp = 1,
                entity = "app-a",
                entityType = EntityType.APP,
                date = date,
                actionsRewarded = 5,
                totalRewardAmount = BigDecimal("422891.777656074258776724"),
                totalImpact = null,
            )
        )
        template.insert(
            UserDailyActionSummary(
                version = 1,
                blockId = "0x2",
                blockNumber = 2,
                blockTimestamp = 2,
                entity = "app-b",
                entityType = EntityType.APP,
                date = date,
                actionsRewarded = 8,
                totalRewardAmount = BigDecimal("422892.777656074258776724"),
                totalImpact = null,
            )
        )

        val threshold = BigDecimal("422891.777656074258776724")

        assertEquals(
            1L,
            userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                threshold,
                EntityType.APP,
                date,
            ),
        )
        assertEquals(
            1L,
            userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                threshold,
                EntityType.APP,
                date,
            ),
        )
    }

    @Test
    fun `round reward rank query accepts decimal thresholds for app entities`() {
        val roundId = 7
        template.insert(
            UserRoundActionSummary(
                version = 1,
                blockId = "0x1",
                blockNumber = 1,
                blockTimestamp = 1,
                entity = "app-a",
                entityType = EntityType.APP,
                roundId = roundId,
                actionsRewarded = 5,
                totalRewardAmount = BigDecimal("4876.273810888064122202"),
                totalImpact = null,
            )
        )
        template.insert(
            UserRoundActionSummary(
                version = 1,
                blockId = "0x2",
                blockNumber = 2,
                blockTimestamp = 2,
                entity = "app-b",
                entityType = EntityType.APP,
                roundId = roundId,
                actionsRewarded = 8,
                totalRewardAmount = BigDecimal("4877.273810888064122202"),
                totalImpact = null,
            )
        )

        val threshold = BigDecimal("4876.273810888064122202")

        assertEquals(
            1L,
            userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                threshold,
                EntityType.APP,
                roundId,
            ),
        )
        assertEquals(
            1L,
            userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                threshold,
                EntityType.APP,
                roundId,
            ),
        )
    }
}
