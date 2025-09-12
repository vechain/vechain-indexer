package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote-results")
open class VeVoteResultConfig {
    @Bean
    open fun vevoteResultIndexer(
        thorClient: ThorClient,
        processor: VeVoteResultProcessor,
        @Value("\${indexer.start-block.vevote}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.vevote}") syncLogInterval: Long,
        @Value("\${veworld.contract.vevote.address}") contractAddress: String,
        @Value("\${indexer.sync-block-batch-size.vevote}") syncBlockBatchSize: Long,
    ): Indexer =
        IndexerFactory()
            .name("VeVoteResultIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/vevote")
            .abiContracts(listOf(contractAddress))
            .abiEventNames(listOf("VoteCast"))
            .excludeVetTransfers()
            .build()
}
