package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("nfts", "history")
open class NftBlacklistConfig {
    @Bean
    open fun nftBlacklistIndexer(
        thorClient: ThorClient,
        processor: NftBlacklistProcessor,
        @Value("\${indexer.start-block.nft-blacklist}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.blacklist.contract-address}") blacklistContract: String,
    ): Indexer {
        require(blacklistContract.isNotBlank()) { "indexer.blacklist.contract-address is required" }
        return IndexerFactory()
            .name(IndexerNames.NFT_BLACKLIST.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/nft")
            .abiContracts(listOf(blacklistContract))
            .abiEventNames(NftBlacklistService.EVENT_NAMES)
            .excludeVetTransfers()
            .build()
    }
}
