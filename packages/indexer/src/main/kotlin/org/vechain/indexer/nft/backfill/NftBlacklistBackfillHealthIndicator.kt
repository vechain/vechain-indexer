package org.vechain.indexer.nft.backfill

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.nft.backfill.NftBlacklistBackfillTask.Status

/** Pending work is not unhealthy, but a synced read needs `pending` to have reached zero. */
@Profile("nfts", "history")
@Component("nftBlacklistBackfillHealthIndicator")
class NftBlacklistBackfillHealthIndicator(private val repository: NftBlacklistBackfillRepository) :
    HealthIndicator {
    override fun health(): Health =
        Health.up().withDetail("pending", repository.countByStatus(Status.PENDING)).build()
}
