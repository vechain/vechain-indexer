package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote", "vevote-comments")
open class VeVoteCommentConfig {
    @Bean
    open fun vevoteCommentIndexer(
        thorClient: ThorClient,
        processor: VeVoteCommentProcessor,
        @Value("\${indexer.start-block.vevote}") startBlock: Long,
        @Value("\${indexer.sync-block-batch-size.vevote}") syncBlockBatchSize: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.VEVOTE_CONTRACT}") contractAddress: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VE_VOTE_COMMENT)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/vevote")
            .abiContracts(listOf(contractAddress))
            .abiEventNames(listOf("VoteCast"))
            .excludeVetTransfers()
            .build()
}
