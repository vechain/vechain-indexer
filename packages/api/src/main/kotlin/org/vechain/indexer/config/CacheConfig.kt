package org.vechain.indexer.config

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
open class CacheConfig(private val cacheProperties: CacheProperties) {

    companion object {
        // All registered caches
        private val CACHE_NAMES =
            listOf(
                "user_all_time_action_countByEntityType",
                "user_all_time_action_countByTotalRewardAmountGreaterThanAndEntityType",
                "user_all_time_action_countByActionsRewardedGreaterThanAndEntityType",
                "app_all_time_action_countByAppId",
                "app_daily_action_countByAppIdAndDate",
                "app_round_countByAppIdAndRoundId",
                "user_daily_action_countByAppIdAndDate",
                "user_daily_action_countByActionsRewardedGreaterThanAndEntityTypeAndDate",
                "user_daily_action_countByEntityTypeAndDate",
                "user_round_countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId",
                "user_round_countByActionsRewardedGreaterThanAndEntityTypeAndRoundId",
                "user_round_countByEntityTypeAndRoundId",
                "official_token_addresses",
                "gmNft_countByLevelAndOwnerNot",
            )
    }

    /**
     * Configures a Caffeine-based cache manager with multiple named caches. Each cache can be
     * configured individually via application.yaml or environment variables, with fallback to
     * global defaults.
     */
    @Bean
    open fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()

        // Register all caches with their configured settings
        CACHE_NAMES.forEach { cacheName ->
            cacheManager.registerCustomCache(cacheName, buildCache(cacheName))
        }

        return cacheManager
    }

    /**
     * Builds a Caffeine cache with settings from properties
     *
     * @param cacheName The name of the cache
     * @return Configured Caffeine cache
     */
    private fun buildCache(cacheName: String): com.github.benmanes.caffeine.cache.Cache<Any, Any> {
        val maxSize = cacheProperties.getMaxSize(cacheName)
        val ttlSeconds = cacheProperties.getTtlSeconds(cacheName)

        return Caffeine.newBuilder()
            .maximumSize(maxSize.toLong())
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .recordStats()
            .build()
    }
}
