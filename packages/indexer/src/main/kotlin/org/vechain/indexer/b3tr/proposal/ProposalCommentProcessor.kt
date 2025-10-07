package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.proposal.repository.ProposalCommentRepository

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Component
open class ProposalCommentProcessor(
    repository: ProposalCommentRepository,
    private val service: ProposalCommentService,
) : BaseProcessor(repository = repository) {
    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val comments = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        service.save(comments)
    }
}
