package org.vechain.indexer.vevote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-historic-proposals")
@Component
open class HistoricProposalsProcessor(
    private val repository: HistoricProposalsRepository,
    private val historicProposalsService: HistoricProposalsService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.HISTORIC_PROPOSALS,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        // No events to process
        if (entry.events().isEmpty()) return

        // Process new proposals or events with their descriptions
        val proposals: List<HistoricProposals> =
            historicProposalsService.processEvents(entry.events())

        // Save the results
        if (proposals.isNotEmpty()) {
            withContext(Dispatchers.IO) { historicProposalsService.save(proposals) }
        }
    }
}
