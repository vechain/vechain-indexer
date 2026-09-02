package org.vechain.indexer.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders

internal class CacheControlTest {

    private val stamp = 1_700_000_000L

    private fun header(response: org.springframework.http.ResponseEntity<*>) =
        response.headers.getFirst(HttpHeaders.CACHE_CONTROL)

    @Test
    fun `the granted age is the age of the content`() {
        assertEquals(3600L, gradedMaxAge(stamp, stamp + 3600))
        assertEquals(86_400L, gradedMaxAge(stamp, stamp + 86_400))
    }

    @Test
    fun `fresh content earns almost nothing`() {
        assertEquals(0L, gradedMaxAge(stamp, stamp))
        assertEquals(10L, gradedMaxAge(stamp, stamp + 10))
    }

    @Test
    fun `deep content is capped`() {
        assertEquals(MAX_CACHE_AGE_SECONDS, gradedMaxAge(stamp, stamp + MAX_CACHE_AGE_SECONDS))
        assertEquals(MAX_CACHE_AGE_SECONDS, gradedMaxAge(stamp, stamp + MAX_CACHE_AGE_SECONDS * 9))
    }

    @Test
    fun `a timestamp ahead of the clock does not grant a negative age`() {
        assertEquals(0L, gradedMaxAge(stamp, stamp - 120))
    }

    @Test
    fun `every policy names its window`() {
        assertEquals("public, max-age=0, s-maxage=10", CachePolicy.VOLATILE.headerValue)
        assertEquals("public, max-age=60", CachePolicy.MINUTE.headerValue)
        assertEquals("public, max-age=600", CachePolicy.TEN_MINUTES.headerValue)
        assertEquals("public, max-age=3600", CachePolicy.HOURLY.headerValue)
        assertEquals("public, max-age=86400", CachePolicy.DAILY.headerValue)
    }

    @Test
    fun `only the volatile tier holds clients to a revalidation`() {
        // A shared cache absorbs a burst at the head; everything else is fine in a browser too.
        val shared = CachePolicy.entries.filter { it.headerValue.contains("s-maxage") }

        assertEquals(listOf(CachePolicy.VOLATILE), shared)
    }

    @Test
    fun `a chosen policy is written onto the response`() {
        assertEquals(CachePolicy.HOURLY.headerValue, header(cachedFor(CachePolicy.HOURLY, "body")))
    }

    @Test
    fun `an aged response carries the age of its content`() {
        assertEquals("public, max-age=3600", header(cachedByAge(stamp, "body", stamp + 3600)))
    }

    @Test
    fun `content with nothing to grade falls back to the volatile tier`() {
        assertEquals(CachePolicy.VOLATILE.headerValue, header(cachedByAge(null, "body")))
    }

    @Test
    fun `the body survives the wrapping`() {
        assertEquals("body", cachedFor(CachePolicy.DAILY, "body").body)
        assertEquals("body", cachedByAge(stamp, "body").body)
    }
}
