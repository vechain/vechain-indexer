package org.vechain.indexer.b3tr.action

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
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryConfig {
    @Bean
    open fun userRoundActionSummaryArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = UserRoundActionSummary::class.java,
            archiveClazz = UserRoundActionSummaryArchive::class.java,
        )
    }

    @Bean
    open fun userRoundActionSummaryPruner(
        userRoundActionSummaryArchiveService:
            ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ) =
        PrunerService(
            klass = UserRoundActionSummaryArchive::class,
            archiveService = userRoundActionSummaryArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun userRoundActionSummaryIndexer(
        thorClient: ThorClient,
        processor: UserRoundActionSummaryProcessor,
        userRoundActionSummaryPruner: Pruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("UserRoundActionSummaryIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(userRoundActionSummaryPruner)
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
