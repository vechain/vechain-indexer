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
open class NavigatorDelegationConfig {

    @Bean
    open fun navigatorDelegationIndexer(
        thorClient: ThorClient,
        processor: NavigatorDelegationProcessor,
        @Value("\${indexer.start-block.b3tr-navigator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-navigator}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.NAVIGATOR_REGISTRY_CONTRACT}")
        navigatorRegistryAddress: String,
        @Value("\${business-event.substitutions.X_ALLOC_VOTING_CONTRACT}")
        xAllocVotingAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NAVIGATOR_DELEGATION.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_DelegationCreated",
                    "B3TR_DelegationUpdated",
                    "B3TR_DelegationRemoved",
                    "B3TR_NavigatorVoteCast",
                )
            )
            .businessEventContracts(listOf(navigatorRegistryAddress, xAllocVotingAddress))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
