package org.vechain.indexer.amn

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("authority-nodes")
open class AmnConfig {
    @Bean
    open fun amnIndexer(
        thorClient: ThorClient,
        processor: AmnProcessor,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.authority-nodes}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.authority-node.address}") contractAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.AUTHORITY_NODE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/amn")
            .abiContracts(listOf(contractAddress))
            .excludeVetTransfers()
            .build()
}
