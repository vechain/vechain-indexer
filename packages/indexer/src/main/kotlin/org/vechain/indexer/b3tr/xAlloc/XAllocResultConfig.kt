package org.vechain.indexer.b3tr.xAlloc

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-x-alloc")
open class XAllocResultConfig {
    @Bean
    open fun xAllocResultArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<XAllocResult, XAllocResultArchive> =
        ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = XAllocResult::class.java,
            archiveClazz = XAllocResultArchive::class.java,
            queryLimit = recordLimit,
        )

    @Bean
    open fun xAllocResultPruner(
        xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(
            klass = XAllocResultArchive::class,
            archiveService = xAllocResultArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun xAllocResultIndexer(
        thorClient: ThorClient,
        processor: XAllocResultProcessor,
        xAllocResultPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-x-alloc-result}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.X_ALLOC_VOTING_CONTRACT}")
        xAllocVotingContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("XAllocResultIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(xAllocResultPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_XAllocationVote"))
            .businessEventContracts(listOf(xAllocVotingContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
