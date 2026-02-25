package org.vechain.indexer.b3tr.vot3

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
@Profile("b3tr", "vot3-balance")
open class Vot3BalanceConfig {

    @Bean
    open fun vot3BalanceArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Vot3Balance, Vot3BalanceArchive> =
        ArchiveService(
            mongoTemplate,
            Vot3Balance::class.java,
            Vot3BalanceArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun vot3BalancePruner(
        vot3BalanceArchiveService: ArchiveService<Vot3Balance, Vot3BalanceArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
        @Value("\${indexer.pruner.enabled}") prunerEnabled: Boolean,
    ): TargetedPruner<Vot3Balance, Vot3BalanceArchive> =
        PrunerService(
            Vot3BalanceArchive::class,
            vot3BalanceArchiveService,
            prunerRemovalChunkSize,
            prunerEnabled,
        )

    @Bean
    open fun vot3BalanceIndexer(
        thorClient: ThorClient,
        processor: Vot3BalanceProcessor,
        vot3BalancePruner: TargetedPruner<Vot3Balance, Vot3BalanceArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.vot3-balance}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.vot3-balance}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.VOT3_CONTRACT}") vot3Contract: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VOT3_BALANCE.NAME)
            .thorClient(thorClient)
            .pruner(vot3BalancePruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .abis("abis/b3tr")
            .abiEventNames(listOf("Transfer"))
            .abiContracts(listOf(vot3Contract))
            .build()
}
