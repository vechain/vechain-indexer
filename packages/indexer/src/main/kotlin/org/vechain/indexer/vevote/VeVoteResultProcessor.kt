package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.thor.model.Block

@Profile("vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    private val repository: VeVoteProposalResultRepository,
) : BaseProcessor(repository = repository) {
    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) return

        // Process votes in the service
        val results = service.processVeVoteResults(events)

        // Save the results
        if (results.isNotEmpty()) {
            repository.saveAll(results)
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
