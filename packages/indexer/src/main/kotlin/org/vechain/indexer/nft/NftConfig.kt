package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("nfts")
open class NftConfig() {

    @Bean
    open fun nftArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<IndexedNft, NftArchive> =
        ArchiveService(mongoTemplate, IndexedNft::class.java, NftArchive::class.java, recordLimit)

    @Bean
    open fun nftPruner(
        nftArchiveService: ArchiveService<IndexedNft, NftArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<IndexedNft, NftArchive> =
        PrunerService(NftArchive::class, nftArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun nftIndexer(
        thorClient: ThorClient,
        processor: NftProcessor,
        nftPruner: TargetedPruner<IndexedNft, NftArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.nfts}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.nfts}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.nfts}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("NftIndexer")
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
