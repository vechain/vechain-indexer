package org.vechain.indexer.utils

import java.util.concurrent.atomic.AtomicReference

private data class CachedValue<T>(val value: T, val timestamp: Long)

/**
 * A simple thread-safe cache that stores a single value with a configurable time-to-live (TTL).
 * After the TTL expires, the cached value is considered stale and will be re-fetched.
 *
 * @param T The type of value to cache
 * @param ttlMs Time-to-live in milliseconds (default: 10 seconds)
 */
class TtlCache<T>(private val ttlMs: Long = DEFAULT_TTL_MS) {
    companion object {
        const val DEFAULT_TTL_MS = 10_000L
    }

    private val cache = AtomicReference<CachedValue<T>?>(null)

    /**
     * Get the cached value if it exists and hasn't expired.
     *
     * @return The cached value, or null if not present or expired
     */
    fun get(): T? {
        val cached = cache.get() ?: return null
        val now = System.currentTimeMillis()
        return if ((now - cached.timestamp) < ttlMs) cached.value else null
    }

    /**
     * Store a value in the cache.
     *
     * @param value The value to cache
     */
    fun set(value: T) {
        cache.set(CachedValue(value, System.currentTimeMillis()))
    }

    /**
     * Get the cached value if valid, otherwise fetch a new value and cache it.
     *
     * @param fetch Suspend function to fetch the value if not cached or expired
     * @return The cached or freshly fetched value
     */
    suspend fun getOrFetch(fetch: suspend () -> T): T {
        get()?.let {
            return it
        }
        val value = fetch()
        set(value)
        return value
    }
}
