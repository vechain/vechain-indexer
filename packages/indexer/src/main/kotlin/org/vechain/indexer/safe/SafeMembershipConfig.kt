package org.vechain.indexer.safe

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
@Profile("safe")
open class SafeMembershipConfig {

    @Bean
    open fun safeMembershipIndexer(
        thorClient: ThorClient,
        processor: SafeMembershipProcessor,
        @Qualifier("safeProxyIndexer") safeProxyIndexer: Indexer,
        @Value("\${indexer.start-block.safe-membership:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.SAFE_MEMBERSHIP.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/safe")
            .abiEventNames(listOf("SafeSetup", "AddedOwner", "RemovedOwner"))
            .dependsOn(safeProxyIndexer)
            .build()
}
