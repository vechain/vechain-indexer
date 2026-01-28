package org.vechain.indexer.transfer

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
@Profile("transfers")
open class FungibleTokenInteractionsConfig(
    private val indexerVersionService: IndexerVersionService
) {
    @Value("\${indexer.version.fungible-token-interactions:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS,
            tableName = "fungible_token_interactions",
            schemaResource = "db/tables/fungible_token_interactions.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun fungibleTokenInteractionsIndexer(
        thorClient: ThorClient,
        processor: FungibleTokenInteractionsProcessor,
        @Value("\${indexer.start-block.transfers}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.transfers}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis/fungible-tokens")
            .abiEventNames(listOf("Transfer"))
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .excludeVetTransfers()
            .build()
}
