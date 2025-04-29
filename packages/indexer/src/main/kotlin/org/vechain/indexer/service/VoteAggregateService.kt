package org.vechain.indexer.service

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VoteAggregate
import org.vechain.indexer.repository.VoteAggregateRepository
import org.vechain.indexer.utils.EventUtils

@Profile("vevote-events")
@Service
class VoteAggregateService(
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
    private val repository: VoteAggregateRepository
) {

    fun getFileCriteria(): FilterCriteria {
        return FilterCriteria(
            contractAddresses = listOf(contractAddress),
            eventNames = listOf("VoteCast"),
        )
    }

    fun processVoteAggregates(processedEvents: List<IndexedEvent>): List<VoteAggregate> {
        val results = mutableListOf<VoteAggregate>()

        // Use the existing event extraction, then transform for aggregation
        val voteEvents =
            processedEvents.mapNotNull { event -> EventUtils.extractVevoteCommentEvent(event) }

        voteEvents.forEach { vote ->
            if (vote.choices.isEmpty()) return@forEach

            // Calculate weight per choice
            val weightPerChoice =
                BigDecimal(vote.weight)
                    .divide(BigDecimal(vote.choices.size), 18, RoundingMode.HALF_UP)

            // Process each choice in the vote
            vote.choices.forEach { choice ->
                // Look for existing aggregate
                val existing = repository.findByProposalIdAndChoice(vote.proposalId, choice)

                // Create or update aggregate
                val updated =
                    if (existing != null) {
                        VoteAggregate(
                            id = "${vote.proposalId}-${choice}",
                            blockId = vote.blockId,
                            blockNumber = vote.blockNumber,
                            blockTimestamp = vote.blockTimestamp,
                            proposalId = vote.proposalId, // You were missing this
                            choice = choice,
                            totalWeight = existing.totalWeight.add(weightPerChoice),
                            voteCount = existing.voteCount + 1
                        )
                    } else {
                        VoteAggregate(
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
