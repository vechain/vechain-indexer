package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.model.generateId
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.utils.CommentUtils.allowComment
import org.vechain.indexer.utils.EventUtils.getChoice

@Profile("vevote-events")
@Service
class CommentService(
    private val repository: VevoteCommentRepository,
    @Value("\${comments.minLength}") private val minLength: Int,
) {

    fun processComment(processedEvents: List<IndexedEvent>): List<VevoteProposalComment> {
        // Process events to extract Reason
        val potentialComment =
            processedEvents.mapNotNull { event -> extractVevoteCommentEvent(event) }
        return potentialComment
            .filter { vote -> vote.reason.isNotEmpty() }
            .filter { vote -> allowComment(vote.proposalId, vote.reason, repository, minLength) }
    }

    fun extractVevoteCommentEvent(event: IndexedEvent): VevoteProposalComment? {
        try {
            val params = event.params
            val voter = params.getReturnValues()["voter"] as? String ?: return null
            val proposalId = params.getReturnValues()["proposalId"]?.toString() ?: return null
            val reason = params.getReturnValues()["reason"] as? String
            val nonNullReasonForId = reason ?: ""
            // Get the raw choice value
            val choiceValue = (params.getReturnValues()["choices"] as? Number)?.toLong() ?: 0L
            val choicesList = getChoice(choiceValue)

            return VevoteProposalComment(
                id = generateId(proposalId, nonNullReasonForId),
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                voter = voter,
                proposalId = proposalId,
                choices = choicesList,
                weight =
                    (params.getReturnValues()["weight"] as? Number)?.toLong()?.toBigInteger()
                        ?: BigInteger.ZERO,
                reason = reason ?: ""
            )
        } catch (e: Exception) {
            return null
        }
    }
}
