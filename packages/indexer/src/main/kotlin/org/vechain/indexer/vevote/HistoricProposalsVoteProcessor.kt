package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("vevote", "vevote-historic-proposals")
open class HistoricProposalsVoteProcessor(
    private val repository: HistoricProposalsVoteRepository,
    private val historicProposalsResultsService: HistoricProposalsVoteService,
    private val historicProposalTallyService: HistoricProposalTallyService,
    private val indexerVersionService: IndexerVersionService,
    @Value("\${indexer.stop-block.historic-proposals}") private val stopBlock: Long,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.HISTORIC_PROPOSALS_VOTE,
        checkpointService = checkpointService,
        collectionName = "historic_proposals_votes",
    ) {
    private var aggregationRan: Boolean = false

    override suspend fun processEntry(entry: IndexingResult) {
        if (aggregationRan) return

        val blockNumber = entry.latestBlockNumber()

        if (blockNumber > stopBlock) {
            historicProposalTallyService.aggregateAllTallies(
                indexerVersionService.getCollectionName(HistoricProposalsVote::class.java)
            )
            aggregationRan = true
            return
        }

        val votes = historicProposalsResultsService.processVotes(entry.events())
        if (votes.isNotEmpty()) {
            repository.saveAll(votes)
        }
    }
}
