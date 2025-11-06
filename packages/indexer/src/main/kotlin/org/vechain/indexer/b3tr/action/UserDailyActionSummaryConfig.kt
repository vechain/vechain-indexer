package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
open class UserDailyActionSummaryConfig {
    @Bean
    open fun userDailyActionSummaryArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<UserDailyActionSummary, UserDailyActionSummaryArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = UserDailyActionSummary::class.java,
            archiveClazz = UserDailyActionSummaryArchive::class.java,
            queryLimit = recordLimit,
        )
    }

    @Bean
    open fun userDailyActionSummaryPruner(
        userDailyActionSummaryArchiveService:
            ArchiveService<UserDailyActionSummary, UserDailyActionSummaryArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<UserDailyActionSummary, UserDailyActionSummaryArchive> =
        PrunerService(
            klass = UserDailyActionSummaryArchive::class,
            archiveService = userDailyActionSummaryArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun userDailyActionSummaryIndexer(
        thorClient: ThorClient,
        processor: UserDailyActionSummaryProcessor,
        userDailyActionSummaryPruner:
            TargetedPruner<UserDailyActionSummary, UserDailyActionSummaryArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.USER_DAILY_ACTION_SUMMARY)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(userDailyActionSummaryPruner)
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
