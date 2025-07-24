package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.vevote.Support
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
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
            val supportRaw = params.getAsBigInteger("support") ?: return@forEach
            val weight = params.getAsBigDecimal("weight") ?: return@forEach

            val support = Support.map(supportRaw)

            val id = "$proposalId-${support.name}"
            val existing = aggregates[id]

            if (existing != null) {
                aggregates[id] =
                    existing.copy(
                        totalWeight = existing.totalWeight.add(weight),
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
                        support = support,
                        totalWeight = weight,
                        totalVoters = 1,
                    )
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
