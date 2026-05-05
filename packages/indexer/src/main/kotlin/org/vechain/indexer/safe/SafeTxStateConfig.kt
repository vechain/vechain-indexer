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
open class SafeTxStateConfig {

    @Bean
    open fun safeTxStateIndexer(
        thorClient: ThorClient,
        processor: SafeTxStateProcessor,
        @Qualifier("safeProxyIndexer") safeProxyIndexer: Indexer,
        @Value("\${indexer.start-block.safe-tx-state:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.SAFE_TX_STATE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/safe")
            .abiEventNames(listOf("ApproveHash", "ExecutionSuccess", "ExecutionFailure"))
            .dependsOn(safeProxyIndexer)
            .build()
}
