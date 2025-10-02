package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-historic-proposals")
@Component
open class HistoricProposalsProcessor(
    private val repository: HistoricProposalsRepository,
    private val historicProposalsService: HistoricProposalsService,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = "HistoricProposalsIndexer",
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            historicProposalsService.processNewProposals(emptyList(), entry.latestBlockNumber())
            return
        }
        val proposals: List<HistoricProposals> =
            historicProposalsService.processNewProposals(entry.events(), entry.latestBlockNumber())

        if (proposals.isNotEmpty()) {
            repository.saveAll(proposals)
        }
    }
}
