package org.vechain.indexer.b3tr.richlist

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.config.CacheProperties
import org.vechain.indexer.config.CacheWarmer

@Profile("b3tr", "b3tr-balance")
@Component
open class B3trRichlistCacheWarmer(
    private val b3trBalanceRepository: B3trBalanceRepository,
    private val b3trRichlistCountService: B3trRichlistCountService,
    private val cacheProperties: CacheProperties,
) : CacheWarmer {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val lastWarmAtMillis = AtomicLong(0)
    private val warming = AtomicBoolean(false)

    override fun warmIfDue() {
        val warmer = cacheProperties.warmers.b3trRichlistTotalHolders
        if (!warmer.enabled) return

        val now = currentTimeMillis()
        val lastWarmAt = lastWarmAtMillis.get()
        if (!isDue(now, lastWarmAt, warmer.refreshIntervalMs)) return

        if (b3trBalanceRepository.getLatestRecord() == null) return

        if (!warming.compareAndSet(false, true)) return

        try {
            val lockedNow = currentTimeMillis()
            val lockedLastWarmAt = lastWarmAtMillis.get()
            if (!isDue(lockedNow, lockedLastWarmAt, warmer.refreshIntervalMs)) return

            RichlistScope.entries.forEach { scope ->
                b3trRichlistCountService.refreshPositiveHolderCount(scope)
            }
            lastWarmAtMillis.set(lockedNow)
            logger.debug("Refreshed B3TR richlist total holder cache")
        } catch (e: Exception) {
            logger.warn("Failed to refresh B3TR richlist total holder cache", e)
            throw e
        } finally {
            warming.set(false)
        }
    }

    protected open fun currentTimeMillis(): Long = Instant.now().toEpochMilli()

    private fun isDue(now: Long, lastWarmAt: Long, refreshIntervalMs: Long): Boolean {
        return lastWarmAt == 0L || now - lastWarmAt >= refreshIntervalMs
    }
}
