package org.vechain.indexer.history

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("history")
open class HistoryConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.history:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.HISTORY,
            tableName = "history_events",
            schemaResource = "db/tables/history_events.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun historyIndexer(
        thorClient: ThorClient,
        processor: HistoryProcessor,
        @Value("\${indexer.start-block.history}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        bEProperties: BusinessEventProperties,
    ): Indexer {
        return IndexerFactory()
            .name(IndexerNames.HISTORY)
            .thorClient(thorClient)
            .syncLoggerInterval(syncLoggerInterval)
            .processor(processor)
            .abis("abis")
            .abiEventNames(listOf("Transfer", "TransferSingle", "TransferBatch"))
            .businessEvents("business-events", "abis")
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .startBlock(startBlock)
            .includeVetTransfers()
            .includeFullBlock()
            .includeVetTransfers()
            .build()
    }
}
