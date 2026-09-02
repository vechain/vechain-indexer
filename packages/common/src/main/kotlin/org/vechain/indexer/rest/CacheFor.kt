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

/** Grants the age of the content; a null timestamp means there was nothing to grade. */
fun <T : Any> cachedByAge(
    blockTimestamp: Long?,
    body: T,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): ResponseEntity<T> =
    cachedResponse(
        blockTimestamp?.let { cacheControlHeader(gradedMaxAge(it, nowEpochSeconds)) }
            ?: CachePolicy.VOLATILE.headerValue,
        body,
    )

private fun <T : Any> cachedResponse(header: String, body: T): ResponseEntity<T> =
    ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, header).body(body)
