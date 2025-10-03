package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryConfig {
    @Bean
    open fun appDailyActionSummaryArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = AppDailyActionSummary::class.java,
            archiveClazz = AppDailyActionSummaryArchive::class.java,
            queryLimit = recordLimit,
        )
    }

    @Bean
    open fun appDailyActionSummaryPruner(
        appDailyActionSummaryArchiveService:
            ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<AppDailyActionSummary, AppDailyActionSummaryArchive> =
        PrunerService(
            klass = AppDailyActionSummaryArchive::class,
            archiveService = appDailyActionSummaryArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun appDailyActionSummaryIndexer(
        thorClient: ThorClient,
        processor: AppDailyActionSummaryProcessor,
        appDailyActionSummaryPruner:
            TargetedPruner<AppDailyActionSummary, AppDailyActionSummaryArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer =
        IndexerFactory()
            .name("AppDailyActionSummaryIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(appDailyActionSummaryPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ActionReward"))
            .businessEventContracts(listOf(b3trContract, x2earnRewardsPoolContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
