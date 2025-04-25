package org.vechain.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.CommentUtils
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.utils.EventUtils

@Profile("vevote-events")
@Service
class commentService(
    private val commentUtils: CommentUtils,
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
) {
    fun getFilterCriteria(): FilterCriteria {
        return FilterCriteria(
            contractAddresses = listOf(contractAddress),
            eventNames = listOf("VoteCast")
        )
    }

    fun processComments(processedEvents: List<IndexedEvent>): List<VevoteProposalComment> {
        // Process events to extract Reason
        val potentialReason =
            processedEvents.mapNotNull { event -> EventUtils.extractVevoteCommentEvent(event) }
        return potentialReason
            .filter { vote -> vote.reason.isNotEmpty() }
            .filter { vote -> commentUtils.allowComment(vote.proposalId, vote.reason) }
    }
}
