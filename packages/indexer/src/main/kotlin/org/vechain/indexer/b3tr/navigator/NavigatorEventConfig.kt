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
@Profile("b3tr", "b3tr-navigator")
open class NavigatorEventConfig {

    @Bean
    open fun navigatorEventIndexer(
        thorClient: ThorClient,
        processor: NavigatorEventProcessor,
        @Value("\${indexer.start-block.b3tr-navigator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-navigator}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.NAVIGATOR_REGISTRY_CONTRACT}")
        navigatorRegistryAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NAVIGATOR_EVENT.NAME)
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
                    "B3TR_ExitFinalized",
                    "B3TR_NavigatorDeactivated",
                    "B3TR_NavigatorSlashed",
                    "B3TR_MetadataURIUpdated",
                    "B3TR_ReportSubmitted",
                )
            )
            .businessEventContracts(listOf(navigatorRegistryAddress))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
