package org.vechain.indexer.rest

import java.time.Instant
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity

/** The reuse window this endpoint hands CloudFront, and the floor the helpers below raise. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CacheFor(val policy: CachePolicy)

/** Grants a policy the handler chose per request. */
fun <T : Any> cachedFor(policy: CachePolicy, body: T): ResponseEntity<T> =
    cachedResponse(policy.headerValue, body)

/**
 * Grants the age of the newest thing the response can cover — the block it settled at, or the
 * instant its window closed. A null, future or just-passed timestamp earns nothing beyond
 * [CachePolicy.VOLATILE], so a window still open cannot freeze at the edge.
 */
fun <T : Any> cachedByAge(
    settledAt: Long?,
    body: T,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): ResponseEntity<T> {
    val age = settledAt?.let { gradedMaxAge(it, nowEpochSeconds) } ?: 0L
    val header =
        if (age > VOLATILE_SHARED_MAX_AGE_SECONDS) cacheControlHeader(age)
        else CachePolicy.VOLATILE.headerValue
    return cachedResponse(header, body)
}

private fun <T : Any> cachedResponse(header: String, body: T): ResponseEntity<T> =
    ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, header).body(body)
