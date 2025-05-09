package org.vechain.indexer.service

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository

@Profile("vevote-result")
@Service
class VoteAggregateService(
    private val repository: VeVoteProposalResultRepository,
    private val commentService: CommentService
) {

    fun processVoteAggregates(processedEvents: List<IndexedEvent>): List<VeVoteProposalResults> {
        val results = mutableListOf<VeVoteProposalResults>()

        // Use the existing event extraction, then transform for aggregation
        val voteEvents =
            processedEvents.mapNotNull { event -> commentService.extractVevoteCommentEvent(event) }

        voteEvents.forEach { vote ->
            if (vote.choices.isEmpty()) return@forEach

            // Calculate weight per choice
            val weightPerChoice =
                BigDecimal(vote.weight)
                    .divide(BigDecimal(vote.choices.size), 18, RoundingMode.HALF_UP)

            // Process each choice in the vote
            vote.choices.forEach { choice ->
                val aggregateId = "${vote.proposalId}-${choice}"
                // Look for existing aggregate by ID
                val existingResult = repository.findById(aggregateId).orElse(null)

                // Create or update aggregate result
                val updated =
                    if (existingResult != null) {
                        VeVoteProposalResults(
                            id = "${vote.proposalId}-${choice}",
                            blockId = vote.blockId,
                            blockNumber = vote.blockNumber,
                            blockTimestamp = vote.blockTimestamp,
                            proposalId = vote.proposalId, // You were missing this
                            choice = choice,
                            totalWeight = existingResult.totalWeight.add(weightPerChoice),
                            voteCount = existingResult.voteCount + 1
                        )
                    } else {
                        VeVoteProposalResults(
                            id = "${vote.proposalId}-${choice}",
                            blockId = vote.blockId,
                            blockNumber = vote.blockNumber,
                            blockTimestamp = vote.blockTimestamp,
                            proposalId = vote.proposalId, // You were missing this
                            choice = choice,
                            totalWeight = weightPerChoice,
                            voteCount = 1
                        )
                    }

                results.add(updated)
            }
        }

        return results
    }
}
