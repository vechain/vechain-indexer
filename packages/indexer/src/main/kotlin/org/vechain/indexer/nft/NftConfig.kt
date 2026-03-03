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
@Profile("nfts")
open class NftConfig() {

    @Bean
    open fun nftIndexer(
        thorClient: ThorClient,
        processor: NftProcessor,
        @Value("\${indexer.start-block.nfts}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.nfts}") syncBlockBatchSize: Long,
        @Value("\${indexer.blacklist.contract-address}") blacklistContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NFT.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/nft")
            .abiEventNames(listOf("Transfer"))
            .businessEvents("business-events/nft", "abis/nft")
            .businessEventNames(listOf("NFT_Blacklisted", "NFT_Whitelisted"))
            .businessEventContracts(listOf(blacklistContract))
            .businessEventSubstitutionParams(
                mapOf("BLACKLIST_CONTRACT_ADDRESS" to blacklistContract)
            )
            .excludeVetTransfers()
            .build()
}
