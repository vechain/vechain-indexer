package org.vechain.indexer.accounts

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
@Profile("accounts", "total-accounts")
open class TotalAccountsConfig {
    @Bean
    open fun totalAccountsArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<TotalAccounts, TotalAccountsArchive> =
        ArchiveService(
            mongoTemplate,
            TotalAccounts::class.java,
            TotalAccountsArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun totalAccountsPruner(
        totalAccountsArchiveService: ArchiveService<TotalAccounts, TotalAccountsArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<TotalAccounts, TotalAccountsArchive> =
        PrunerService(
            TotalAccountsArchive::class,
            totalAccountsArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun totalAccountsIndexer(
        thorClient: ThorClient,
        processor: TotalAccountsProcessor,
        totalAccountsPruner: TargetedPruner<TotalAccounts, TotalAccountsArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.TOTAL_ACCOUNTS_INDEXER)
            .thorClient(thorClient)
            .pruner(totalAccountsPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .build()
}
