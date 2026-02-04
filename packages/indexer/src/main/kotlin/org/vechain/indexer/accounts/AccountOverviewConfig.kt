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
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("accounts", "account-overview")
open class AccountOverviewConfig {
    @Bean
    open fun accountOverviewArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<AccountOverview, AccountOverviewArchive> =
        ArchiveService(
            mongoTemplate,
            AccountOverview::class.java,
            AccountOverviewArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun accountOverviewPruner(
        accountOverviewArchiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<AccountOverview, AccountOverviewArchive> =
        PrunerService(
            AccountOverviewArchive::class,
            accountOverviewArchiveService,
            prunerRemovalChunkSize,
        )

    @Bean
    open fun accountOverviewIndexer(
        thorClient: ThorClient,
        processor: AccountOverviewProcessor,
        accountOverviewPruner: TargetedPruner<AccountOverview, AccountOverviewArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.ACCOUNT_OVERVIEW_INDEXER)
            .thorClient(thorClient)
            .pruner(accountOverviewPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .includeFullBlock()
            .includeVetTransfers()
            .abis("abis/other")
            .abiEventNames(listOf("Transfer"))
            .abiContracts(listOf(VTHO_CONTRACT_ADDRESS))
            .build()
}
