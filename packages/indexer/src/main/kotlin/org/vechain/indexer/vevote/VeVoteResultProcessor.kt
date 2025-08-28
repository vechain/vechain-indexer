package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    repository: VeVoteProposalResultRepository,
) : BaseProcessor(repository = repository) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return

        // Process votes in the service
        service.processVeVoteResults(matchedEvents)
    }
}
