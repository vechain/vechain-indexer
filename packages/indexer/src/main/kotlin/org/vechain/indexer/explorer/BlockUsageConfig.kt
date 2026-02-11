package org.vechain.indexer.explorer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("explorer", "block-usage")
open class BlockUsageConfig {
    @Bean
    open fun blockUsageIndexer(
        thorClient: ThorClient,
        processor: BlockUsageProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.BLOCK_USAGE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(syncLoggerInterval)
            .startBlock(0L)
            .includeFullBlock()
            .build()
}
