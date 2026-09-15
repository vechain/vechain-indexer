package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.vevote.VeVoteEventUtils.getProposalId
import org.vechain.indexer.vevote.VeVoteEventUtils.getWeight
import org.vechain.indexer.vevote.VeVoteEventUtils.groupByProposalId
import org.vechain.indexer.vevote.VeVoteEventUtils.groupBySupport

@Profile("vevote")
@Service
open class VeVoteResultService(private val repository: VeVoteWriteRepository) {

    /** The new row of each (proposal, support) a vote moved, in ascending block order. */
    open fun processEvents(events: List<IndexedEvent>): List<VeVoteProposalResult> {
        assertEventTypes(events, "VoteCast")
        if (events.isEmpty()) return emptyList()

        val current =
            repository
                .findCurrentResults(events.map(::getProposalId).toSet())
                .associateBy { it.proposalId to it.support }
                .toMutableMap()
        // One row per (block, proposal, support): a later block supersedes the earlier row.
        val rows = linkedMapOf<Triple<Long, String, Support>, VeVoteProposalResult>()

        groupByBlock(events).forEach { (block, blockEvents) ->
            groupByProposalId(blockEvents).forEach { (proposalId, proposalEvents) ->
                groupBySupport(proposalEvents).forEach { (support, votes) ->
                    val key = proposalId to support
                    val updated = addVotes(block, proposalId, support, votes, current[key])
                    current[key] = updated
                    rows[Triple(block.blockNumber, proposalId, support)] = updated
                }
            }
        }

        return rows.values.toList()
    }

    private fun addVotes(
        block: BlockDetails,
        proposalId: String,
        support: Support,
        votes: List<IndexedEvent>,
        existing: VeVoteProposalResult?,
    ): VeVoteProposalResult {
        val weight = votes.sumOf { getWeight(it) }

        return existing?.copy(
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
            totalWeight = existing.totalWeight + weight,
            totalVoters = existing.totalVoters + votes.size,
        )
            ?: VeVoteProposalResult(
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                proposalId = proposalId,
                support = support,
                totalWeight = weight,
                totalVoters = votes.size,
            )
    }
}
