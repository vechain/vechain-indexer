package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
open class UserAllTimeActionSummaryConfig {
    @Bean
    open fun userAllTimeActionSummaryArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = UserAllTimeActionSummary::class.java,
            archiveClazz = UserAllTimeActionSummaryArchive::class.java,
            queryLimit = recordLimit,
        )
    }

    @Bean
    open fun userAllTimeActionSummaryPruner(
        userAllTimeActionSummaryArchiveService:
            ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ) =
        org.vechain.indexer.pruner.PrunerService(
            klass = UserAllTimeActionSummaryArchive::class,
            archiveService = userAllTimeActionSummaryArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun userAllTimeActionSummaryIndexer(
        thorClient: org.vechain.indexer.thor.client.ThorClient,
        processor: UserAllTimeActionSummaryProcessor,
        userAllTimeActionSummaryPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): org.vechain.indexer.Indexer =
        org.vechain.indexer
            .IndexerFactory()
            .name("UserAllTimeActionSummaryIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(userAllTimeActionSummaryPruner)
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
