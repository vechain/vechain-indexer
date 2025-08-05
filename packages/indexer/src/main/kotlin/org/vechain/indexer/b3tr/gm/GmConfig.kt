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

@Configuration
@Profile("b3tr", "galaxy-member")
open class GmConfig {

    @Bean
    open fun gmArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<GmLevelOverview, GmLevelOverviewArchive> =
        ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = GmLevelOverview::class.java,
            archiveClazz = GmLevelOverviewArchive::class.java,
        )

    @Bean
    open fun gmPruner(
        gmArchiveService: ArchiveService<GmLevelOverview, GmLevelOverviewArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            klass = GmLevelOverviewArchive::class,
            archiveService = gmArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean open fun gmIndexer(): Indexer = IndexerFactory().build()
}
