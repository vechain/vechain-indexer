package org.vechain.indexer.config

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

interface CacheWarmer {
    fun warmIfDue()
}

@Component
open class CacheWarmingScheduler(
    private val cacheProperties: CacheProperties,
    private val cacheWarmers: List<CacheWarmer>,
) {

    @Scheduled(fixedDelayString = "\${cache.warmers.tick-interval-ms:60000}")
    open fun warmCaches() {
        if (!cacheProperties.warmers.enabled) return
        cacheWarmers.forEach { it.warmIfDue() }
    }
}
