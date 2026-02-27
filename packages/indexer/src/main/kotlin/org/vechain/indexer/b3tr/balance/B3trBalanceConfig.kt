package org.vechain.indexer.b3tr.balance

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-balance")
open class B3trBalanceConfig {

    @Bean
    open fun b3trBalanceArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<B3trBalance, B3trBalanceArchive> =
        ArchiveService(
            mongoTemplate,
            B3trBalance::class.java,
            B3trBalanceArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun b3trBalancePruner(
        b3trBalanceArchiveService: ArchiveService<B3trBalance, B3trBalanceArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
        @Value("\${indexer.pruner.enabled}") prunerEnabled: Boolean,
    ): TargetedPruner<B3trBalance, B3trBalanceArchive> =
        PrunerService(
            B3trBalanceArchive::class,
            b3trBalanceArchiveService,
            prunerRemovalChunkSize,
            prunerEnabled,
        )

    @Bean
    open fun b3trBalanceIndexer(
        thorClient: ThorClient,
        processor: B3trBalanceProcessor,
        b3trBalancePruner: TargetedPruner<B3trBalance, B3trBalanceArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-balance}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-balance}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.B3TR_BALANCE.NAME)
            .thorClient(thorClient)
            .pruner(b3trBalancePruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .abis("abis/b3tr")
            .abiEventNames(listOf("Transfer"))
            .abiContracts(listOf(b3trContract))
            .build()
}
