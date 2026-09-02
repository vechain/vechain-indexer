package org.vechain.indexer.rest

/** However deep the content sits, it never earns a longer TTL than this. */
const val MAX_CACHE_AGE_SECONDS = 31_536_000L

/** One block: what a shared cache may hold a still-moving response for, when a client may not. */
const val VOLATILE_SHARED_MAX_AGE_SECONDS = 10L

/** How long a response may be reused. See AGENTS.md "Endpoints Own Their Cache TTL". */
enum class CachePolicy(private val maxAgeSeconds: Long, private val sharedMaxAgeSeconds: Long?) {
    /** Moves with the head: shared caches absorb a burst, clients always revalidate. */
    VOLATILE(0, VOLATILE_SHARED_MAX_AGE_SECONDS),

    /** Follows on-chain activity, but a stale minute costs the caller nothing. */
    MINUTE(60, null),

    /** An aggregate over a window that only shifts every few minutes. */
    TEN_MINUTES(600, null),

    /** Rolled up hourly, or slow enough that an hour of staleness is invisible. */
    HOURLY(3_600, null),

    /** Rolled up per UTC day. */
    DAILY(86_400, null);

    val headerValue: String = cacheControlHeader(maxAgeSeconds, sharedMaxAgeSeconds)
}

/** The age of the content, so a TTL never outlives the span it has already been stable. */
fun gradedMaxAge(blockTimestamp: Long, nowEpochSeconds: Long): Long =
    (nowEpochSeconds - blockTimestamp).coerceIn(0L, MAX_CACHE_AGE_SECONDS)

internal fun cacheControlHeader(maxAgeSeconds: Long, sharedMaxAgeSeconds: Long? = null): String =
    "public, max-age=$maxAgeSeconds" + sharedMaxAgeSeconds?.let { ", s-maxage=$it" }.orEmpty()
