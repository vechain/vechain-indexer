package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.insert
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult

@Profile("vevote", "vevote-comments")
@Component
open class VeVoteCommentProcessor(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val veVoteCommentService: VeVoteCommentService,
    private val mongoTemplate: MongoTemplate,
) : BaseProcessor(repository = vevoteCommentRepository, indexerName = IndexerNames.VEVOTE_COMMENT) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Filter events to only those related to VeVote comments
        val allowedReason = veVoteCommentService.processComment(entry.events())

        // Save the results
        if (allowedReason.isNotEmpty()) {
            mongoTemplate.insert<VeVoteProposalComment>(allowedReason)
        }
    }
}
