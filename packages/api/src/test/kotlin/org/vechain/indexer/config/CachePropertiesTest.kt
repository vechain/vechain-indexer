package org.vechain.indexer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CachePropertiesTest {

    @Test
    fun `getMaxSize returns cache-specific max size when configured`() {
        // Given
        val properties =
            CacheProperties(
                maxSize = 1000,
                ttlSeconds = 3600,
                caches =
                    mapOf(
                        "official_token_addresses" to
                            CacheProperties.CacheSpec(maxSize = 10, ttlSeconds = null)
                    ),
            )

        // When
        val result = properties.getMaxSize("official_token_addresses")

        // Then
        assertEquals(10, result)
    }

    @Test
    fun `getMaxSize returns global default when cache-specific not configured`() {
        // Given
        val properties = CacheProperties(maxSize = 1000, ttlSeconds = 3600, caches = emptyMap())

        // When
        val result = properties.getMaxSize("some_other_cache")

        // Then
        assertEquals(1000, result)
    }

    @Test
    fun `getMaxSize returns global default when cache-specific max size is null`() {
        // Given
        val properties =
            CacheProperties(
                maxSize = 1000,
                ttlSeconds = 3600,
                caches =
                    mapOf(
                        "official_token_addresses" to
                            CacheProperties.CacheSpec(maxSize = null, ttlSeconds = 7200)
                    ),
            )

        // When
        val result = properties.getMaxSize("official_token_addresses")

        // Then
        assertEquals(1000, result)
    }

    @Test
    fun `getTtlSeconds returns cache-specific TTL when configured`() {
        // Given
        val properties =
            CacheProperties(
                maxSize = 1000,
                ttlSeconds = 3600,
                caches =
                    mapOf(
                        "official_token_addresses" to
                            CacheProperties.CacheSpec(maxSize = null, ttlSeconds = 7200)
                    ),
            )

        // When
        val result = properties.getTtlSeconds("official_token_addresses")

        // Then
        assertEquals(7200, result)
    }

    @Test
    fun `getTtlSeconds returns global default when cache-specific not configured`() {
        // Given
        val properties = CacheProperties(maxSize = 1000, ttlSeconds = 3600, caches = emptyMap())

        // When
        val result = properties.getTtlSeconds("some_other_cache")

        // Then
        assertEquals(3600, result)
    }

    @Test
    fun `getTtlSeconds returns global default when cache-specific TTL is null`() {
        // Given
        val properties =
            CacheProperties(
                maxSize = 1000,
                ttlSeconds = 3600,
                caches =
                    mapOf(
                        "official_token_addresses" to
                            CacheProperties.CacheSpec(maxSize = 10, ttlSeconds = null)
                    ),
            )

        // When
        val result = properties.getTtlSeconds("official_token_addresses")

        // Then
        assertEquals(3600, result)
    }

    @Test
    fun `handles multiple cache configurations correctly`() {
        // Given
        val properties =
            CacheProperties(
                maxSize = 1000,
                ttlSeconds = 3600,
                caches =
                    mapOf(
                        "official_token_addresses" to
                            CacheProperties.CacheSpec(maxSize = 10, ttlSeconds = 7200),
                        "user_all_time_action_countByEntityType" to
                            CacheProperties.CacheSpec(maxSize = 500, ttlSeconds = 1800),
                        "app_all_time_action_countByAppId" to
                            CacheProperties.CacheSpec(maxSize = 2000, ttlSeconds = null),
                    ),
            )

        // When/Then
        assertEquals(10, properties.getMaxSize("official_token_addresses"))
        assertEquals(7200, properties.getTtlSeconds("official_token_addresses"))

        assertEquals(500, properties.getMaxSize("user_all_time_action_countByEntityType"))
        assertEquals(1800, properties.getTtlSeconds("user_all_time_action_countByEntityType"))

        assertEquals(2000, properties.getMaxSize("app_all_time_action_countByAppId"))
        assertEquals(3600, properties.getTtlSeconds("app_all_time_action_countByAppId"))

        assertEquals(1000, properties.getMaxSize("unconfigured_cache"))
        assertEquals(3600, properties.getTtlSeconds("unconfigured_cache"))
    }
}
