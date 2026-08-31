package org.vechain.indexer.rest

/** However deep the content sits, it never earns a longer TTL than this. */
const val MAX_CACHE_AGE_SECONDS = 31_536_000L

/** Sent when a response can still change: shared caches absorb bursts, clients revalidate. */
const val VOLATILE_CACHE_CONTROL = "public, max-age=0, s-maxage=10"

/** The age of the content, so a TTL never outlives the span it has already been stable. */
fun gradedMaxAge(blockTimestamp: Long, nowEpochSeconds: Long): Long =
    (nowEpochSeconds - blockTimestamp).coerceIn(0L, MAX_CACHE_AGE_SECONDS)

fun cacheControlFor(maxAgeSeconds: Long): String = "public, max-age=$maxAgeSeconds"
