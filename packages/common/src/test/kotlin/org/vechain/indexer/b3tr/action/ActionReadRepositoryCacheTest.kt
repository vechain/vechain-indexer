package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.vechain.indexer.b3tr.shared.EntityType

/** The rank counts behind a proxy like the API's, so the key expressions are proven, not read. */
internal class ActionReadRepositoryCacheTest {

    @Configuration
    @EnableCaching
    open class Caching {
        @Bean open fun cacheManager(): CacheManager = ConcurrentMapCacheManager(CACHE)
    }

    private val jdbc = mockk<JdbcTemplate>()
    private val context =
        AnnotationConfigApplicationContext().apply {
            register(Caching::class.java)
            registerBean(ActionReadRepository::class.java, Supplier { ActionReadRepository(jdbc) })
            refresh()
        }
    private val repository = context.getBean(ActionReadRepository::class.java)
    private val cache = context.getBean(CacheManager::class.java).getCache(CACHE)!!

    @Test
    fun `a rank count is keyed on the period, the entity type and the exact value`() {
        every { jdbc.queryForObject(any<String>(), any<Class<Long>>(), *anyVararg<Any>()) } returns
            7L

        repeat(2) {
            assertEquals(
                7L,
                repository.countEntitiesAbove(
                    ActionPeriod.AllTime,
                    EntityType.USER,
                    ActionSortField.TOTAL_REWARD_AMOUNT,
                    BigDecimal("1.50"),
                ),
            )
        }

        verify(exactly = 1) {
            jdbc.queryForObject(any<String>(), any<Class<Long>>(), *anyVararg<Any>())
        }
        assertNotNull(cache.get("entity|AllTime|USER|TOTAL_REWARD_AMOUNT|1.50"))
    }

    @Test
    fun `an app's rank count is keyed on the app and its period`() {
        every { jdbc.queryForObject(any<String>(), any<Class<Long>>(), *anyVararg<Any>()) } returns
            2L
        val appId = "0x" + "7".repeat(64)

        repository.countAppUsersAbove(
            ActionPeriod.Day("2026-09-01"),
            appId,
            ActionSortField.ACTIONS_REWARDED,
            BigDecimal(3),
        )

        assertNotNull(cache.get("app|Day(date=2026-09-01)|$appId|ACTIONS_REWARDED|3"))
    }

    companion object {
        private const val CACHE = "b3tr_action_rank_counts"
    }
}
