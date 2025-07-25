package org.vechain.indexer.historicalProposal

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.HistoricalProposals

@Profile("historical-proposals")
@Service
class HistoricalProposalsService() {

    fun processNewProposals(processedEvents: List<IndexedEvent>): List<HistoricalProposals> =
        processedEvents.mapNotNull { extractNewProposalEvent(it) }

    fun extractNewProposalEvent(event: IndexedEvent): HistoricalProposals? {
        val params = event.params.getReturnValues()

        val proposalId = params["proposalId"]?.toString() ?: return null
        val creator = params["creator"] as? String ?: return null
        val proposalType = params["ptype"] as? Int

        return HistoricalProposals(
            id = "${event.address}-${proposalId}",
            proposalId = proposalId,
            createdDate = event.blockTimestamp.toString(),
            proposalType = proposalType,
            choices = null,
            createTime = null,
            votingStartTime = null,
            votingEndTime = null,
            voteTallies = null,
            totalVotes = null,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            title = null,
        )
    }
}
