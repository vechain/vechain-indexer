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
import org.vechain.indexer.model.IndexedNft
import org.vechain.indexer.model.NftArchive
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("nfts")
open class NftConfig() {

    @Bean
    open fun nftArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<IndexedNft, NftArchive> =
        ArchiveService(mongoTemplate, IndexedNft::class.java, NftArchive::class.java)

    @Bean
    open fun nftPruner(
        nftArchiveService: ArchiveService<IndexedNft, NftArchive>,
        @Value("\${indexer.pruner.removalChunkSize}") prunerRemovalChunkSize: Int,
    ): Pruner = PrunerService(NftArchive::class, nftArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun nftIndexer(
        thorClient: ThorClient,
        processor: NftProcessor,
        nftPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.startBlock.nfts}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.nfts}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.nfts}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("NFTIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(nftPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/nft")
            .abiEventNames(listOf("Transfer"))
            .excludeVetTransfers()
            .build()
}
