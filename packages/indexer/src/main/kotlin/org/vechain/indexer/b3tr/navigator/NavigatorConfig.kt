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

/** A block indexer: an exit deadline passes without an event, so every block is checked. */
@Configuration
@Profile("b3tr", "b3tr-navigator")
open class NavigatorConfig {

    @Bean
    open fun navigatorIndexer(
        thorClient: ThorClient,
        processor: NavigatorProcessor,
        @Value("\${indexer.start-block.b3tr-navigator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.NAVIGATOR_REGISTRY_CONTRACT}")
        navigatorRegistryAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NAVIGATOR.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(NavigatorService.EVENTS + NavigatorFeeService.EVENTS)
            .businessEventContracts(listOf(navigatorRegistryAddress))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .excludeVetTransfers()
            .build()
}
