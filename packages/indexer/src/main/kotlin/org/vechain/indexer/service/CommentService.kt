package org.vechain.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.utils.CommentUtils
import org.vechain.indexer.utils.EventUtils

@Profile("vevote-events")
@Service
class CommentService(
    private val repository: VevoteCommentRepository,
    @Value("\${comments.minLength}") private val minLength: Int,
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
) {
    fun getFilterCriteria(): FilterCriteria {
        return FilterCriteria(
            contractAddresses = listOf(contractAddress),
            eventNames = listOf("VoteCast")
        )
    }

    fun processComment(processedEvents: List<IndexedEvent>): List<VevoteProposalComment> {
        // Process events to extract Reason
        val potentialComment =
            processedEvents.mapNotNull { event -> EventUtils.extractVevoteCommentEvent(event) }
        return potentialComment
            .filter { vote -> vote.reason.isNotEmpty() }
            .filter { vote ->
                CommentUtils.allowComment(vote.proposalId, vote.reason, repository, minLength)
            }
    }
}
