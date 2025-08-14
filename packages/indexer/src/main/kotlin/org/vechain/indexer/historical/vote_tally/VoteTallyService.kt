package org.vechain.indexer.historical.vote_tally

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService

@Profile("historical-proposals")
@Service
open class VoteTallyService(
    private val thorService: ThorService,
    @Value("\${veworld.contract.historical_proposals.steering_committee}")
    private val steeringCommitteeAddress: String,
    @Value("\${veworld.contract.historical_proposals.all_stakeholders}")
    private val allStakeholdersAddress: String,
) {

    fun processNewVotes(events: List<IndexedEvent>, currentBlockNumber: Long?): List<VoteTally> {
        return events
    }
} // end
