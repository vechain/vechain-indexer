package org.vechain.indexer.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CacheControlTest {

    private val stamp = 1_700_000_000L

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
    fun `the header names the granted age`() {
        assertEquals("public, max-age=3600", cacheControlFor(3600L))
        assertEquals("public, max-age=0", cacheControlFor(0L))
    }
}
