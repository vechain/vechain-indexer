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
open class CacheConfig {

    /**
     * Configures a Caffeine-based cache manager with multiple named caches.
     *
     * Caches:
     * - vnsNames: VNS name resolution results
     * - functionAbi: ABI function definitions (no expiration as ABIs are immutable)
     */
    @Bean
    open fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()

        // Configure individual caches with specific settings
        cacheManager.registerCustomCache(
            "user_all_time_action_countByEntityType",
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(1, TimeUnit.HOURS).build(),
        )

        cacheManager.registerCustomCache(
            "user_all_time_action_countByTotalRewardAmountGreaterThanAndEntityType",
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(1, TimeUnit.HOURS).build(),
        )

        cacheManager.registerCustomCache(
            "user_all_time_action_countByActionsRewardedGreaterThanAndEntityType",
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(1, TimeUnit.HOURS).build(),
        )

        cacheManager.registerCustomCache(
            "app_all_time_action_countByAppId",
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(1, TimeUnit.HOURS).build(),
        )

        cacheManager.registerCustomCache(
            "official_token_addresses",
            Caffeine.newBuilder().maximumSize(10).expireAfterWrite(1, TimeUnit.HOURS).build(),
        )

        return cacheManager
    }
}
