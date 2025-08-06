package org.vechain.indexer.historical

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("historical-proposals")
@Component
open class HistoricalProposalsProcessor(
    private val repository: HistoricalProposalsRepository,
    private val historicalProposalsService: HistoricalProposalsService,
) : BaseProcessor(repository = repository) {

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            historicalProposalsService.processNewProposals(emptyList(), block?.number)
            return
        }
        val proposals: List<HistoricalProposals> =
            historicalProposalsService.processNewProposals(matchedEvents, block?.number)

        if (proposals.isNotEmpty()) {
            repository.saveAll(proposals)
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
