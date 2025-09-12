package org.vechain.indexer.historical

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("historical-proposals")
open class HistoricalProposalsConfig {
    @Bean
    open fun historicalProposalsIndexer(
        thorClient: ThorClient,
        processor: HistoricalProposalsProcessor,
        @Value("\${indexer.start-block.historical-proposals}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.historical-proposals}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.historical-proposals}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.historical-proposals.steering-committee}")
        steeringCommittee: String,
        @Value("\${veworld.contract.historical-proposals.all-stakeholders}") allStakeholders: String,
    ): Indexer =
        IndexerFactory()
            .name("historicalProposalsIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/historical-proposals")
            .abiContracts(listOf(steeringCommittee, allStakeholders))
            .abiEventNames(listOf("NewProposal"))
            .excludeVetTransfers()
            .build()
}
