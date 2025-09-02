package org.vechain.indexer.b3tr.voting

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.b3tr.voting.repository.ProposalCommentRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("b3tr", "b3tr-voting", "b3tr-proposal-comments")
@Component
open class ProposalCommentProcessor(
    repository: ProposalCommentRepository,
    private val service: ProposalCommentService,
) : BaseProcessor(repository = repository) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        val comments = service.processEvents(matchedEvents)

        // Save the updated NFTs and archives
        service.save(comments)
    }
}
