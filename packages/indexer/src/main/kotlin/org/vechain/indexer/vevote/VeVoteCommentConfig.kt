package org.vechain.indexer.vevote

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
@Profile("vevote", "vevote-comments")
open class VeVoteCommentConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.vevote-comments:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.VEVOTE_COMMENT,
            tableName = "vevote_proposal_comments",
            schemaResource = "db/tables/vevote_proposal_comments.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun vevoteCommentIndexer(
        thorClient: ThorClient,
        processor: VeVoteCommentProcessor,
        @Value("\${indexer.start-block.vevote}") startBlock: Long,
        @Value("\${indexer.sync-block-batch-size.vevote}") syncBlockBatchSize: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.VEVOTE_CONTRACT}") contractAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VEVOTE_COMMENT)
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
