package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult

@Profile("vevote", "vevote-comments")
@Component
open class VeVoteCommentProcessor(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val veVoteCommentService: VeVoteCommentService,
    private val mongoTemplate: MongoTemplate,
) : BaseProcessor(repository = vevoteCommentRepository) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Filter events to only those related to VeVote comments
        val allowedReason = veVoteCommentService.processComment(entry.events())

        // Save the results
        if (allowedReason.isNotEmpty()) {
            mongoTemplate.insert(allowedReason, VeVoteProposalComment::class.java)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
