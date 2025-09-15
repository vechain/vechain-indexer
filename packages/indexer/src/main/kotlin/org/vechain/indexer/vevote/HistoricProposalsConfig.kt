package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("vevote", "vevote-historic-proposals")
open class HistoricProposalsConfig {
    @Bean
    open fun historicalProposalsIndexer(
        thorClient: ThorClient,
        processor: HistoricProposalsProcessor,
        @Value("\${indexer.start-block.historic-proposals}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.historic-proposals}") syncLogInterval: Long,
        @Value("\${indexer.sync-block-batch-size.historic-proposals}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.historic-proposals.steering-committee}")
        steeringCommittee: String,
        @Value("\${veworld.contract.historic-proposals.all-stakeholders}") allStakeholders: String,
    ): Indexer =
        IndexerFactory()
            .name("HistoricalProposalsIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/historic-proposals")
            .abiContracts(listOf(steeringCommittee, allStakeholders))
            .abiEventNames(listOf("NewProposal"))
            .excludeVetTransfers()
            .build()
}
