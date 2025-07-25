package org.vechain.indexer.historicalProposal

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
        @Value("\${indexer.startBlock.historical_proposals}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.historical_proposals}") syncLogInterval: Long,
        @Value("\${indexer.syncBlockBatchSize.historical_proposals}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.historical_proposals.steering_committee}")
        steeringCommittee: String,
        @Value("\${veworld.contract.historical_proposals.all_stakeholders}") allStakeholders: String,
    ): Indexer =
        IndexerFactory()
            .name("historicalProposalsIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/historical-Proposals")
            .abiContracts(listOf(steeringCommittee, allStakeholders))
            .abiEventNames(listOf("NewProposal"))
            .excludeVetTransfers()
            .build()
}
