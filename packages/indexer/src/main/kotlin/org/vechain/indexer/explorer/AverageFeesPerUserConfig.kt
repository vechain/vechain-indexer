package org.vechain.indexer.explorer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("explorer", "average-fees-per-user")
open class AverageFeesPerUserConfig {
    @Bean
    open fun averageFeesPerUserIndexer(
        thorClient: ThorClient,
        processor: AverageFeesPerUserProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.start-block.average-fees-per-user:0}") startBlock: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.AVERAGE_FEES_PER_USER.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .build()
}
