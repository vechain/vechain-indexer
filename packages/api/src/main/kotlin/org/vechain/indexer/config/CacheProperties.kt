package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    var maxSize: Int = 1000,
    var ttlSeconds: Long = 3600, // 1 hour default
    var caches: Map<String, CacheSpec> = emptyMap(),
    var warmers: Warmers = Warmers(),
) {
    data class CacheSpec(var maxSize: Int? = null, var ttlSeconds: Long? = null)

    data class Warmers(
        var enabled: Boolean = true,
        var tickIntervalMs: Long = 60_000,
        var b3trRichlistTotalHolders: WarmerSpec = WarmerSpec(),
    )

    data class WarmerSpec(var enabled: Boolean = false, var refreshIntervalMs: Long = 540_000)

    /** Gets the effective max size for a cache, falling back to global default if not specified */
    fun getMaxSize(cacheName: String): Int {
        return caches[cacheName]?.maxSize ?: maxSize
    }

    /**
     * Gets the effective TTL in seconds for a cache, falling back to global default if not
     * specified
     */
    fun getTtlSeconds(cacheName: String): Long {
        return caches[cacheName]?.ttlSeconds ?: ttlSeconds
    }
}
