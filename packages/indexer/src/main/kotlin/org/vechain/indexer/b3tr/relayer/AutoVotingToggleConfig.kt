package org.vechain.indexer.b3tr.relayer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-auto-voting-toggles")
open class AutoVotingToggleConfig {
    @Bean
    open fun autoVotingToggleIndexer(
        thorClient: ThorClient,
        processor: AutoVotingToggleProcessor,
        @Value("\${indexer.start-block.b3tr-auto-voting-toggles}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-auto-voting-toggles}")
        syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.X_ALLOC_VOTING_CONTRACT}")
        xAllocVotingContract: String,
        @Value("\${business-event.substitutions.EMISSIONS}") emissionsContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.AUTO_VOTING_TOGGLE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/b3tr")
            .abiEventNames(
                listOf("EmissionDistributed", "EmissionDistributedV2", "AutoVotingToggled")
            )
            .abiContracts(listOf(emissionsContract, xAllocVotingContract))
            .build()
}
