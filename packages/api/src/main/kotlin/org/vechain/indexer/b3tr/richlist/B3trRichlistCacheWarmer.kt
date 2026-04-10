package org.vechain.indexer.b3tr.richlist

import java.time.Instant
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

    override fun warmIfDue() {
        val warmer = cacheProperties.warmers.b3trRichlistTotalHolders
        if (!warmer.enabled) return

        val now = Instant.now().toEpochMilli()
        val lastWarmAt = lastWarmAtMillis.get()
        if (lastWarmAt != 0L && now - lastWarmAt < warmer.refreshIntervalMs) return

        if (b3trBalanceRepository.getLatestRecord() == null) return

        if (!lastWarmAtMillis.compareAndSet(lastWarmAt, now)) return

        RichlistScope.entries.forEach { scope ->
            b3trRichlistCountService.refreshPositiveHolderCount(scope)
        }
        logger.debug("Refreshed B3TR richlist total holder cache")
    }
}
