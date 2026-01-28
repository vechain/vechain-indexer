package org.vechain.indexer.explorer

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames.BLOCK_USAGE
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("explorer", "block-usage")
open class BlockUsageConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.block-usage:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = BLOCK_USAGE,
            tableName = "block_usage",
            schemaResource = "db/tables/block_usage.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun blockUsageIndexer(
        thorClient: ThorClient,
        processor: BlockUsageProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(BLOCK_USAGE)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(syncLoggerInterval)
            .startBlock(0L)
            .includeFullBlock()
            .build()
}
