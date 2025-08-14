package org.vechain.indexer.historical.vote_tally

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("historical-proposals")
@Component
open class VoteTallyProcessor(
    private val repository: VoteTallyRepository,
    private val voteTallyService: VoteTallyService,
) : BaseProcessor(repository = repository) {

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }
        val votes = voteTallyService.processNewVotes(matchedEvents, block?.number)
        if (votes.isNotEmpty()) {
            repository.saveAll(votes)
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
