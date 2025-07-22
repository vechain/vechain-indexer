package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

@Profile("nfts", "history")
@EnableScheduling
@Component
class NftBlacklistScheduler(
    private val indexers: List<Indexer>,
    private val nftBlacklistService: NftBlacklistService,
) {
    @Scheduled(
        initialDelayString = "\${indexer.blacklist.initialDelay}",
        fixedRateString = "\${indexer.blacklist.interval}",
    )
    fun syncBlacklist() {
        // Only sync if all indexers are fully synced
        if (indexers.any { it.status != Status.FULLY_SYNCED }) {
            return
        }
        nftBlacklistService.syncBlacklistedNFTs()
        nftBlacklistService.syncBlacklistedNFTsFromHistory()
    }
}
