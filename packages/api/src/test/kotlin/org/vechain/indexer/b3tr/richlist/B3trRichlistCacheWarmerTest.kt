package org.vechain.indexer.b3tr.richlist

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
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.config.CacheProperties

@ExtendWith(MockKExtension::class)
internal class B3trRichlistCacheWarmerTest {

    @MockK lateinit var b3trBalanceRepository: B3trBalanceRepository
    @MockK lateinit var b3trRichlistCountService: B3trRichlistCountService

    private lateinit var cacheProperties: CacheProperties
    private lateinit var warmer: B3trRichlistCacheWarmer

    @BeforeEach
    fun setUp() {
        cacheProperties = CacheProperties()
        cacheProperties.warmers.b3trRichlistTotalHolders.enabled = true
        cacheProperties.warmers.b3trRichlistTotalHolders.refreshIntervalMs = 0
        warmer =
            B3trRichlistCacheWarmer(
                b3trBalanceRepository,
                b3trRichlistCountService,
                cacheProperties,
            )
    }

    @Test
    fun `warmer refreshes all scopes when collection is not empty`() {
        every { b3trBalanceRepository.getLatestRecord() } returns balance(blockNumber = 99)
        every { b3trRichlistCountService.refreshPositiveHolderCount(any()) } returns 42L

        warmer.warmIfDue()

        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.ALL)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.B3TR)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.VOT3)
        }
    }

    @Test
    fun `warmer skips refresh when collection is empty`() {
        every { b3trBalanceRepository.getLatestRecord() } returns null

        warmer.warmIfDue()

        verify(exactly = 0) { b3trRichlistCountService.refreshPositiveHolderCount(any()) }
    }

    @Test
    fun `warmer skips immediate refresh when interval has not elapsed`() {
        cacheProperties.warmers.b3trRichlistTotalHolders.refreshIntervalMs = 1_000
        warmer = testWarmer(listOf(10_000L, 10_000L, 10_000L))
        every { b3trBalanceRepository.getLatestRecord() } returns balance(blockNumber = 99)
        every { b3trRichlistCountService.refreshPositiveHolderCount(any()) } returns 42L

        warmer.warmIfDue()
        warmer.warmIfDue()

        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.ALL)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.B3TR)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.VOT3)
        }
    }

    @Test
    fun `warmer only refreshes once per interval with overlapping calls`() {
        cacheProperties.warmers.b3trRichlistTotalHolders.refreshIntervalMs = 1_000
        warmer = testWarmer(generateSequence { 10_000L }.iterator())

        val firstRepositoryCallStarted = CountDownLatch(1)
        val releaseFirstRepositoryCall = CountDownLatch(1)
        val repositoryCallCount = AtomicInteger(0)

        every { b3trBalanceRepository.getLatestRecord() } answers
            {
                if (repositoryCallCount.incrementAndGet() == 1) {
                    firstRepositoryCallStarted.countDown()
                    assertTrue(releaseFirstRepositoryCall.await(2, TimeUnit.SECONDS))
                }
                balance(blockNumber = 99)
            }
        every { b3trRichlistCountService.refreshPositiveHolderCount(any()) } returns 42L

        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<Unit> { warmer.warmIfDue() }
            assertTrue(firstRepositoryCallStarted.await(2, TimeUnit.SECONDS))

            val second = executor.submit<Unit> { warmer.warmIfDue() }
            second.get(2, TimeUnit.SECONDS)

            releaseFirstRepositoryCall.countDown()
            first.get(2, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.ALL)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.B3TR)
        }
        verify(exactly = 1) {
            b3trRichlistCountService.refreshPositiveHolderCount(RichlistScope.VOT3)
        }
    }

    private fun testWarmer(times: List<Long>): B3trRichlistCacheWarmer =
        testWarmer(times.iterator())

    private fun testWarmer(times: Iterator<Long>): B3trRichlistCacheWarmer =
        object :
            B3trRichlistCacheWarmer(
                b3trBalanceRepository,
                b3trRichlistCountService,
                cacheProperties,
            ) {
            override fun currentTimeMillis(): Long = times.next()
        }

    private fun balance(blockNumber: Long): B3trBalance =
        B3trBalance(
            address = "0xaddr",
            blockId = "0xblock",
            blockNumber = blockNumber,
            blockTimestamp = 1_000L,
            version = 1,
            vot3Balance = java.math.BigDecimal.ONE,
            b3trBalance = java.math.BigDecimal.ONE,
            totalBalance = java.math.BigDecimal("2"),
        )
}
