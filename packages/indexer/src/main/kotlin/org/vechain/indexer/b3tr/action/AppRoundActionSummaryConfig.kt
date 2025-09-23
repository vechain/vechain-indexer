package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryConfig {
    @Bean
    open fun appRoundActionSummaryArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive> {
        return ArchiveService(
            mongoTemplate = mongoTemplate,
            clazz = AppRoundActionSummary::class.java,
            archiveClazz = AppRoundActionSummaryArchive::class.java,
            queryLimit = recordLimit,
        )
    }

    @Bean
    open fun appRoundActionSummaryPruner(
        appRoundActionSummaryArchiveService:
            ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<AppRoundActionSummary, AppRoundActionSummaryArchive> =
        PrunerService(
            klass = AppRoundActionSummaryArchive::class,
            archiveService = appRoundActionSummaryArchiveService,
            prunerRemovalChunkSize = prunerRemovalChunkSize,
        )

    @Bean
    open fun appRoundActionSummaryIndexer(
        thorClient: ThorClient,
        processor: AppRoundActionSummaryProcessor,
        appRoundActionSummaryPruner:
            TargetedPruner<AppRoundActionSummary, AppRoundActionSummaryArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr-sustainable-actions}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.b3tr}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        @Value("\${business-event.substitutions.EMISSIONS}") emissionsContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name("AppRoundActionSummaryIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .pruner(appRoundActionSummaryPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/b3tr")
            .abiEventNames(listOf("EmissionDistributed", "EmissionDistributedV2"))
            .abiContracts(listOf(emissionsContract))
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ActionReward"))
            .businessEventContracts(listOf(b3trContract, x2earnRewardsPoolContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .excludeVetTransfers()
            .build()
}
