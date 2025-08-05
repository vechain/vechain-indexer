package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote-comments")
open class VeVoteCommentConfig {
    @Bean
    open fun vevoteCommentIndexer(
        thorClient: ThorClient,
        processor: VeVoteCommentProcessor,
        @Value("\${indexer.start-block.vevote}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.vevote}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.vevote}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.vevote.address}") contractAddress: String,
    ): Indexer =
        IndexerFactory()
            .name("VeVoteCommentIndexer")
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
