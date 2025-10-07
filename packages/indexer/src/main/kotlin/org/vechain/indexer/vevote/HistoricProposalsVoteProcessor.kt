package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult

@Component
@Profile("vevote-historic-proposals")
open class HistoricProposalsVoteProcessor(
    private val repository: HistoricProposalsVoteRepository,
    private val historicProposalsResultsService: HistoricProposalsVoteService,
    private val historicProposalTallyService: HistoricProposalTallyService,
) : BaseProcessor(repository = repository) {
    private var aggregationRan: Boolean = false
    private val stopBlock: Long = 22933000

    override fun process(entry: IndexingResult) {
        if (aggregationRan) return

        val blockNumber = entry.latestBlockNumber()

        if (blockNumber > stopBlock) {
            historicProposalTallyService.aggregateAllTallies()
            aggregationRan = true
            return
        }

        val votes = historicProposalsResultsService.processVotes(entry.events())
        if (votes.isNotEmpty()) {
            repository.saveAll(votes)
        }
    }
}
