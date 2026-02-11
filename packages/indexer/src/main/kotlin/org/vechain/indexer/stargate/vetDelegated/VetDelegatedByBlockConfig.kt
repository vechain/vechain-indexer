package org.vechain.indexer.stargate.vetDelegated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vet-delegated-by-block")
open class VetDelegatedByBlockConfig {
    @Bean
    open fun vetDelegatedByBlockIndexer(
        thorClient: ThorClient,
        processor: VetDelegatedByBlockProcessor,
        @Qualifier("delegationIndexer") delegationIndexer: Indexer,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VET_DELEGATED_BY_BLOCK.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .dependsOn(delegationIndexer)
            .build()
}
