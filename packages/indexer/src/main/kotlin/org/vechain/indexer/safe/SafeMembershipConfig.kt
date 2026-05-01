package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
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
        @Value("\${indexer.start-block.safe-membership:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.safe-membership:100}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.SAFE_MEMBERSHIP.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/safe")
            .abiEventNames(listOf("SafeSetup", "AddedOwner", "RemovedOwner"))
            .build()
}
