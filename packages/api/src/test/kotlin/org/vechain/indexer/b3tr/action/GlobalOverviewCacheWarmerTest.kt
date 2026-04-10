package org.vechain.indexer.b3tr.action

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.CacheProperties

@ExtendWith(MockKExtension::class)
internal class GlobalOverviewCacheWarmerTest {

    @MockK lateinit var globalOverviewCountService: GlobalOverviewCountService

    private lateinit var cacheProperties: CacheProperties
    private lateinit var warmer: GlobalOverviewCacheWarmer

    @BeforeEach
    fun setUp() {
        cacheProperties = CacheProperties()
        cacheProperties.warmers.globalOverviewCounts.enabled = true
        cacheProperties.warmers.globalOverviewCounts.refreshIntervalMs = 0
        warmer = GlobalOverviewCacheWarmer(globalOverviewCountService, cacheProperties)
    }

    @Test
    fun `warmer refreshes user count`() {
        every { globalOverviewCountService.refreshCountByEntityType(any()) } returns 42L

        warmer.warmIfDue()

        verify(exactly = 1) { globalOverviewCountService.refreshCountByEntityType(EntityType.USER) }
    }

    @Test
    fun `warmer does not refresh when disabled`() {
        cacheProperties.warmers.globalOverviewCounts.enabled = false

        warmer.warmIfDue()

        verify(exactly = 0) { globalOverviewCountService.refreshCountByEntityType(any()) }
    }

    @Test
    fun `warmer skips refresh when interval has not elapsed`() {
        cacheProperties.warmers.globalOverviewCounts.refreshIntervalMs = 1_000
        warmer = testWarmer(listOf(10_000L, 10_000L, 10_000L))
        every { globalOverviewCountService.refreshCountByEntityType(any()) } returns 42L

        warmer.warmIfDue()
        warmer.warmIfDue()

        verify(exactly = 1) { globalOverviewCountService.refreshCountByEntityType(EntityType.USER) }
    }

    @Test
    fun `warmer only refreshes once per interval with overlapping calls`() {
        cacheProperties.warmers.globalOverviewCounts.refreshIntervalMs = 1_000
        warmer = testWarmer(generateSequence { 10_000L }.iterator())

        val firstCallStarted = CountDownLatch(1)
        val releaseFirstCall = CountDownLatch(1)
        val callCount = AtomicInteger(0)

        every { globalOverviewCountService.refreshCountByEntityType(any()) } answers
            {
                if (callCount.incrementAndGet() == 1) {
                    firstCallStarted.countDown()
                    assertTrue(releaseFirstCall.await(2, TimeUnit.SECONDS))
                }
                42L
            }

        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<Unit> { warmer.warmIfDue() }
            assertTrue(firstCallStarted.await(2, TimeUnit.SECONDS))

            val second = executor.submit<Unit> { warmer.warmIfDue() }
            second.get(2, TimeUnit.SECONDS)

            releaseFirstCall.countDown()
            first.get(2, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        verify(exactly = 1) { globalOverviewCountService.refreshCountByEntityType(EntityType.USER) }
    }

    private fun testWarmer(times: List<Long>): GlobalOverviewCacheWarmer =
        testWarmer(times.iterator())

    private fun testWarmer(times: Iterator<Long>): GlobalOverviewCacheWarmer =
        object : GlobalOverviewCacheWarmer(globalOverviewCountService, cacheProperties) {
            override fun currentTimeMillis(): Long = times.next()
        }
}
