package org.vechain.indexer.config

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.HistoryIndexer
import org.vechain.indexer.NFTBlacklistIndexer
import org.vechain.indexer.NFTEventIndexer
import org.vechain.indexer.Status
import org.vechain.indexer.service.NFTBlacklistService

@EnableScheduling
@Component
class NFTBlacklistScheduler(
    private val nftEventIndexer: NFTEventIndexer,
    private val historyIndexer: HistoryIndexer,
    private val nftBlacklistIndexer: NFTBlacklistIndexer,
    private val nftBlacklistService: NFTBlacklistService,
) {
    @Scheduled(
        initialDelayString = "\${indexer.blacklist.initialDelay}",
        fixedRateString = "\${indexer.blacklist.interval}",
    )
    fun syncBlacklist() {
        val nftIndexerSynced = nftEventIndexer.status == Status.FULLY_SYNCED
        val historyIndexerSynced = historyIndexer.status == Status.FULLY_SYNCED
        if (
            nftBlacklistIndexer.status == Status.FULLY_SYNCED &&
                (nftIndexerSynced || historyIndexerSynced)
        ) {
            if (nftIndexerSynced) nftBlacklistService.syncBlacklistedNFTs()
            if (historyIndexerSynced) nftBlacklistService.syncBlacklistedNFTsFromHistory()
        }
    }
}
