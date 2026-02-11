package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("vevote", "vevote-historic-proposals")
@Component
open class HistoricProposalsProcessor(
    private val repository: HistoricProposalsRepository,
    private val historicProposalsService: HistoricProposalsService,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.HISTORIC_PROPOSALS,
        checkpointService = checkpointService,
        collectionName = "historic_proposals",
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        // No events to process
        if (entry.events().isEmpty()) return

        // Process new proposals or events with their descriptions
        val proposals: List<HistoricProposals> =
            historicProposalsService.processEvents(entry.events())

        // Save the results
        if (proposals.isNotEmpty()) {
            historicProposalsService.save(proposals)
        }
    }
}
