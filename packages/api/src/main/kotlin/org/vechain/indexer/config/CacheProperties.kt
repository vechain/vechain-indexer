package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    var maxSize: Int = 1000,
    var ttlSeconds: Long = 3600, // 1 hour default
    var caches: Map<String, CacheSpec> = emptyMap(),
) {
    data class CacheSpec(var maxSize: Int? = null, var ttlSeconds: Long? = null)

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
