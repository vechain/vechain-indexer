package org.vechain.indexer.b3tr.balance

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-balance")
open class B3trBalanceConfig {

    @Bean
    open fun b3trBalanceIndexer(
        thorClient: ThorClient,
        processor: B3trBalanceProcessor,
        @Value("\${indexer.start-block.b3tr-balance}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-balance}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.VOT3_CONTRACT}") vot3Contract: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.B3TR_BALANCE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/b3tr")
            .abiEventNames(listOf("Transfer"))
            .abiContracts(listOf(b3trContract, vot3Contract))
            .build()
}
