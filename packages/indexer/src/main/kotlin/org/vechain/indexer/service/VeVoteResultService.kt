package org.vechain.indexer.service

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("vevote-results")
@Service
class VeVoteResultService(private val repository: VeVoteProposalResultRepository) {
    fun processVeVoteResults(processedEvents: List<IndexedEvent>): List<VeVoteProposalResults> {
        val inMemoryAggregates = aggregateFromEvents(processedEvents)
        return mergeWithExisting(inMemoryAggregates)
    }

    private fun aggregateFromEvents(
        events: List<IndexedEvent>
    ): Map<String, VeVoteProposalResults> {
        val aggregates = mutableMapOf<String, VeVoteProposalResults>()

        events.forEach { vote ->
            val params = vote.params

            val proposalId = params.getAsString("proposalId") ?: return@forEach
            val choiceValue = params.getAsLong("choices") ?: return@forEach
            val weight = params.getAsBigDecimal("weight") ?: return@forEach

            val choices = EventUtils.getChoice(choiceValue)
            if (choices.isEmpty()) return@forEach

            val weightPerChoice = weight.divide(BigDecimal(choices.size), 18, RoundingMode.HALF_UP)

            choices.forEach { choice ->
                val id = "$proposalId-$choice"
                val existing = aggregates[id]

                if (existing != null) {
                    aggregates[id] =
                        existing.copy(
                            totalWeight = existing.totalWeight.add(weightPerChoice),
                            totalVoters = existing.totalVoters + 1,
                        )
                } else {
                    aggregates[id] =
                        VeVoteProposalResults(
                            id = id,
                            blockId = vote.blockId,
                            blockNumber = vote.blockNumber,
                            blockTimestamp = vote.blockTimestamp,
                            proposalId = proposalId,
                            choice = choice,
                            totalWeight = weightPerChoice,
                            totalVoters = 1,
                        )
                }
            }
        }

        return aggregates
    }

    private fun mergeWithExisting(
        aggregates: Map<String, VeVoteProposalResults>
    ): List<VeVoteProposalResults> =
        aggregates.values.map { agg ->
            val existing = repository.findById(agg.id).orElse(null)
            if (existing != null) {
                agg.copy(
                    totalWeight = existing.totalWeight.add(agg.totalWeight),
                    totalVoters = existing.totalVoters + agg.totalVoters,
                )
            } else {
                agg
            }
        }
}
