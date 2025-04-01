package org.vechain.indexer.config

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.NFTBlacklistIndexer
import org.vechain.indexer.NFTEventIndexer
import org.vechain.indexer.Status
import org.vechain.indexer.service.NFTBlacklistService

@EnableScheduling
@Component
class NFTBlacklistScheduler(
    private val nftEventIndexer: NFTEventIndexer,
    private val nftBlacklistIndexer: NFTBlacklistIndexer,
    private val nftBlacklistService: NFTBlacklistService
) {

    @Scheduled(
        initialDelayString = "\${indexer.blacklist.initialDelay}",
        fixedRateString = "\${indexer.blacklist.interval}"
    )
    fun syncBlacklist() {
        // Only run if both indexers are fully synced
        if (
            nftBlacklistIndexer.status == Status.FULLY_SYNCED &&
                nftEventIndexer.status == Status.FULLY_SYNCED
        ) {
            nftBlacklistService.syncBlacklistedNFTs()
        }
    }
}
