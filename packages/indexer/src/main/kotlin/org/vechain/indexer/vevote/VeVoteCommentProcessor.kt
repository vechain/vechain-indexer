package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("vevote-comments")
@Component
open class VeVoteCommentProcessor(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val veVoteCommentService: VeVoteCommentService,
    private val mongoTemplate: MongoTemplate,
) : BaseProcessor(repository = vevoteCommentRepository) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return

        // Filter events to only those related to VeVote comments
        val allowedReason = veVoteCommentService.processComment(matchedEvents)

        // Save the results
        if (allowedReason.isNotEmpty()) {
            mongoTemplate.insert(allowedReason, VeVoteProposalComment::class.java)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
