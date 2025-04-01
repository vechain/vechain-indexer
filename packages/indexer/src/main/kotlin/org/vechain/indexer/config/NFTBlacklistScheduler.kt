package org.vechain.indexer.config

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.service.NFTBlacklistService

@EnableScheduling
@Component
class NFTBlacklistScheduler(private val nftBlacklistService: NFTBlacklistService) {

    @Scheduled(
        initialDelayString = "\${indexer.blacklist.initialDelay}",
        fixedRateString = "\${indexer.blacklist.interval}"
    )
    fun runPruners() {
        nftBlacklistService.syncBlacklistedNFTs()
    }
}
