package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Component
@Profile("vevote-historic-proposals")
open class HistoricProposalsVoteProcessor(
    private val repository: HistoricProposalsVoteRepository,
    private val historicProposalsResultsService: HistoricProposalsVoteService,
    private val historicProposalTallyService: HistoricProposalTallyService,
) : BaseProcessor(repository = repository) {
    private var aggregationRan: Boolean = false
    private val stopBlock: Long = 21051206

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (aggregationRan) return

        val blockNumber = block?.number ?: 0

        if (blockNumber > stopBlock) {
            historicProposalTallyService.aggregateAllTallies()
            aggregationRan = true
            return
        }

        val votes = historicProposalsResultsService.processVotes(matchedEvents)
        if (votes.isNotEmpty()) {
            repository.saveAll(votes)
        }
    }
}
