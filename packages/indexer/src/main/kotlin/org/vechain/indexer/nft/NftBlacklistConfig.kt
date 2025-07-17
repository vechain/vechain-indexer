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
import org.vechain.indexer.model.NftBlacklist
import org.vechain.indexer.model.NftBlacklistArchive
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.FileUtils

@Configuration
@Profile("nfts")
open class NftBlacklistConfig {
    @Bean
    open fun nftBlacklistArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<NftBlacklist, NftBlacklistArchive> =
        ArchiveService(mongoTemplate, NftBlacklist::class.java, NftBlacklistArchive::class.java)

    @Bean
    open fun nftBlacklistPruner(
        nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>,
        @Value("\${indexer.pruner.removalChunkSize}") prunerRemovalChunkSize: Int,
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
        @Value("\${indexer.startBlock.nfts}") startBlock: Long,
        @Value("\${indexer.blacklist.contract_address}") blacklistContract: String,
        @Value("\${indexer.syncLogInterval.nfts}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.nfts}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("NFTBlacklistIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(nftBlacklistPruner)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .abiFiles(FileUtils.getJsonFilePaths("abis/nft"))
            .abiEventNames(listOf("NFTBlacklisted", "NFTWhitelisted"))
            .abiContracts(listOf(blacklistContract))
            .blockBatchSize(syncBlockBatchSize)
            .excludeVetTransfers()
            .build()
}
