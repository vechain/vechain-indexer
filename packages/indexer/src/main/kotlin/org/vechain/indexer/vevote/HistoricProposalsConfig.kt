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
@Profile("vevote-historic-proposals")
open class HistoricProposalsConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.historic-proposals:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.HISTORIC_PROPOSALS,
            tableName = "historic_proposals",
            schemaResource = "db/tables/historic_proposals.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun historicProposalsIndexer(
        thorClient: ThorClient,
        processor: HistoricProposalsProcessor,
        @Value("\${indexer.start-block.historic-proposals}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.historic-proposals}") syncBlockBatchSize: Long,
        @Value("\${veworld.contract.historic-proposals.steering-committee}")
        steeringCommittee: String,
        @Value("\${veworld.contract.historic-proposals.all-stakeholders}") allStakeholders: String,
        @Value("\${veworld.contract.historic-proposals.legacy-descriptions}")
        legacyDescriptions: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.HISTORIC_PROPOSALS)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/historic-proposals")
            .abiContracts(listOf(steeringCommittee, allStakeholders, legacyDescriptions))
            .abiEventNames(listOf("NewProposal", "LegacyVeVoteDescription"))
            .excludeVetTransfers()
            .build()
}
