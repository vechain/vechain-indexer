package org.vechain.indexer.vevote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("vevote", "vevote-historic-proposals")
open class HistoricProposalsVoteProcessor(
    private val repository: HistoricProposalsVoteRepository,
    private val historicProposalsResultsService: HistoricProposalsVoteService,
    private val historicProposalTallyService: HistoricProposalTallyService,
    indexerVersionService: IndexerVersionService,
    @Value("\${indexer.stop-block.historic-proposals}") private val stopBlock: Long,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.HISTORIC_PROPOSALS_VOTE,
    ) {
    private var aggregationRan: Boolean = false

    override suspend fun processEntry(entry: IndexingResult) {
        if (aggregationRan) return

        val blockNumber = entry.latestBlockNumber()

        if (blockNumber > stopBlock) {
            historicProposalTallyService.aggregateAllTallies(repository.getCollectionName())
            aggregationRan = true
            return
        }

        val votes = historicProposalsResultsService.processVotes(entry.events())
        if (votes.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.saveAll(votes) }
        }
    }
}
