package org.vechain.indexer.b3tr.proposal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.proposal.repository.ProposalCommentRepository
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Component
open class ProposalCommentProcessor(
    repository: ProposalCommentRepository,
    private val service: ProposalCommentService,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.PROPOSAL_COMMENT,
        checkpointService = checkpointService,
        collectionName = "b3tr_proposal_comments",
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val comments = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        if (comments.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(comments) }
        }
    }
}
