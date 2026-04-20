package org.vechain.indexer.b3tr.navigator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-overview-summary")
open class NavigatorOverviewSummaryConfig {
    @Bean
    open fun navigatorOverviewSummaryIndexer(
        thorClient: ThorClient,
        processor: NavigatorOverviewSummaryProcessor,
        @Value("\${indexer.start-block.b3tr-navigator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-navigator}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.NAVIGATOR_REGISTRY_CONTRACT}")
        navigatorRegistryAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NAVIGATOR_OVERVIEW_SUMMARY.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_NavigatorRegistered",
                    "B3TR_StakeAdded",
                    "B3TR_StakeWithdrawn",
                    "B3TR_ExitAnnounced",
                    "B3TR_NavigatorDeactivated",
                    "B3TR_NavigatorSlashed",
                    "B3TR_NavigatorMinorSlashed",
                    "B3TR_DelegationCreated",
                    "B3TR_DelegationIncreased",
                    "B3TR_DelegationDecreased",
                    "B3TR_DelegationRemoved",
                )
            )
            .businessEventContracts(listOf(navigatorRegistryAddress))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
