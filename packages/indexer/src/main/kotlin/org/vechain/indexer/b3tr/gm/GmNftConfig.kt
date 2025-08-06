package org.vechain.indexer.b3tr.gm

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
@Profile("b3tr", "gm-nft")
open class GmNftConfig {

    @Bean
    open fun gmNftArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<GmNft, GmNftArchive> =
        ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = GmNft::class.java,
            archiveClazz = GmNftArchive::class.java,
        )

    @Bean
    open fun gmNftPruner(
        gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            klass = GmNftArchive::class,
            archiveService = gmNftArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun gmNftIndexer(
        thorClient: ThorClient,
        processor: GmNftProcessor,
        gmNftPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
    ): Indexer = IndexerFactory().build()
}
