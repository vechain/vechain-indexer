package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("stargate", "vet-delegated-by-block")
open class VetDelegatedByBlockConfig {
    @Bean
    open fun vetDelegatedByBlockIndexer(
        thorClient: ThorClient,
        processor: VetDelegatedByBlockProcessor,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VET_DELEGATED_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/stargate")
            .abiContracts(listOf(stargateContract))
            .abiEventNames(listOf("DelegationInitiated", "DelegationWithdrawn"))
            .excludeVetTransfers()
            .build()
}
