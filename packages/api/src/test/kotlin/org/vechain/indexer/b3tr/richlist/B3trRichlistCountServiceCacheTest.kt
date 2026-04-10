package org.vechain.indexer.b3tr.richlist

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [B3trRichlistCountServiceCacheTest.TestConfig::class])
internal class B3trRichlistCountServiceCacheTest {

    @Configuration
    @EnableCaching
    open class TestConfig {
        @Bean open fun cacheManager(): CacheManager = ConcurrentMapCacheManager(CACHE_NAME)

        @Bean open fun mongoTemplate(): MongoTemplate = mockk(relaxed = true)

        @Bean
        open fun b3trRichlistCountService(mongoTemplate: MongoTemplate): B3trRichlistCountService =
            B3trRichlistCountService(mongoTemplate)
    }

    @Autowired private lateinit var service: B3trRichlistCountService
    @Autowired private lateinit var mongoTemplate: MongoTemplate
    @Autowired private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun setUp() {
        cacheManager.getCache(CACHE_NAME)?.clear()
        clearMocks(mongoTemplate)
    }

    @Test
    fun `repeated total holder count for same scope uses cache`() {
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } returns 42L

        val first = service.getPositiveHolderCount(RichlistScope.ALL)
        val second = service.getPositiveHolderCount(RichlistScope.ALL)

        assertEquals(42L, first)
        assertEquals(42L, second)
        verify(exactly = 1) { mongoTemplate.count(any<Query>(), any(), any<String>()) }
    }

    @Test
    fun `different scopes use different cache entries`() {
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } returnsMany listOf(7L, 9L)

        val all = service.getPositiveHolderCount(RichlistScope.ALL)
        val vot3 = service.getPositiveHolderCount(RichlistScope.VOT3)

        assertEquals(7L, all)
        assertEquals(9L, vot3)
        verify(exactly = 2) { mongoTemplate.count(any<Query>(), any(), any<String>()) }
    }

    @Test
    fun `concurrent total holder count for same scope is computed once`() {
        every { mongoTemplate.count(any<Query>(), any(), any<String>()) } answers
            {
                Thread.sleep(100)
                42L
            }
        val executor = Executors.newFixedThreadPool(4)

        try {
            val futures =
                List(4) {
                    executor.submit(Callable { service.getPositiveHolderCount(RichlistScope.ALL) })
                }

            futures.forEach { assertEquals(42L, it.get(2, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }

        verify(exactly = 1) { mongoTemplate.count(any<Query>(), any(), any<String>()) }
    }

    companion object {
        private const val CACHE_NAME = "b3tr_richlist_total_holders"
    }
}
