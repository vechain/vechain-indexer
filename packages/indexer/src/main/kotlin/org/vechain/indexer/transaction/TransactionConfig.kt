package org.vechain.indexer.transaction

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("transactions")
open class TransactionConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.transactions:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.TRANSACTION,
            tableName = "transactions",
            schemaResource = "db/tables/transactions.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun transactionIndexer(
        thorClient: ThorClient,
        processor: TransactionProcessor,
        @Value("\${indexer.start-block.transactions}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.TRANSACTION)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis")
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .excludeVetTransfers()
            .includeFullBlock()
            .build()
}
