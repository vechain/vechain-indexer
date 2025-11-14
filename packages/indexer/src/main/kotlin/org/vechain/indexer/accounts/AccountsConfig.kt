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
@Profile("accounts")
open class AccountsConfig {
    @Bean
    open fun accountsArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Accounts, AccountsArchive> =
        ArchiveService(
            mongoTemplate,
            Accounts::class.java,
            AccountsArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun accountsPruner(
        accountsArchiveService: ArchiveService<Accounts, AccountsArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<Accounts, AccountsArchive> =
        PrunerService(AccountsArchive::class, accountsArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun accountsIndexer(
        thorClient: ThorClient,
        processor: AccountsProcessor,
        accountsPruner: TargetedPruner<Accounts, AccountsArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNTS_INDEXER)
            .thorClient(thorClient)
            .pruner(accountsPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .build()
}
