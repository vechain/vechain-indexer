package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("vevote-results")
@Service
class VeVoteResultService(
    private val repository: VeVoteProposalResultRepository,
    private val vevoteResultArchiveService:
        ArchiveService<VeVoteProposalResults, VeVoteProposalResultsArchive>,
) {
    fun processVeVoteResults(events: List<IndexedEvent>) {
        val aggregates = aggregateFromEvents(events)
        if (aggregates.isEmpty()) return

        val existing = repository.findAllById(aggregates.keys).associateBy { it.id }

        val voteResults =
            aggregates.values.map { agg ->
                val old = existing[agg.id]
                if (old != null) {
                    agg.copy(
                        totalWeight = old.totalWeight.add(agg.totalWeight),
                        totalVoters = old.totalVoters + agg.totalVoters,
                        blockId = agg.blockId,
                        blockNumber = agg.blockNumber,
                        blockTimestamp = agg.blockTimestamp,
                        version = old.version + 1,
                    )
                } else {
                    agg
                }
            }

        repository.saveAll(voteResults)

        if (existing.isNotEmpty()) {
            vevoteResultArchiveService.saveAll(existing.values.toList())
        }
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
                        blockNumber = vote.blockNumber,
                        blockTimestamp = vote.blockTimestamp,
                        blockId = vote.blockId,
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
                        version = 1,
                    )
            }
        }

        return aggregates
    }
}
