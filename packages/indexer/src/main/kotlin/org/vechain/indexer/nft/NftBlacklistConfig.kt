package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("nfts", "history")
open class NftBlacklistConfig {
    @Bean
    open fun nftBlacklistArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<NftBlacklist, NftBlacklistArchive> =
        ArchiveService(mongoTemplate, NftBlacklist::class.java, NftBlacklistArchive::class.java)

    @Bean
    open fun nftBlacklistPruner(
        nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            NftBlacklistArchive::class,
            nftBlacklistArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun nftBlacklistIndexer(
        thorClient: ThorClient,
        processor: NftBlacklistProcessor,
        nftBlacklistPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.nft-blacklist}") startBlock: Long,
        @Value("\${indexer.blacklist.contract-address}") blacklistContract: String,
        @Value("\${indexer.sync-log-interval.nfts}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.nfts}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("NftBlacklistIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(nftBlacklistPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .abis("abis/nft")
            .abiEventNames(listOf("NFTBlacklisted", "NFTWhitelisted"))
            .abiContracts(listOf(blacklistContract))
            .blockBatchSize(syncBlockBatchSize)
            .excludeVetTransfers()
            .build()
}
