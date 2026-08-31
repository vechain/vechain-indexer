package org.vechain.indexer.blocks

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("blocks")
open class BlocksConfig {
    // includeFullBlock() is mandatory: IndexerFactory.build() only returns a BlockIndexer when
    // includeFullBlock or dependsOn is set, and here the block header is the data.
    @Bean
    open fun blocksIndexer(
        thorClient: ThorClient,
        processor: BlocksProcessor,
        @Value("\${indexer.start-block.blocks:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.BLOCKS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(syncLoggerInterval)
            .startBlock(startBlock)
            .includeFullBlock()
            .build()
}
