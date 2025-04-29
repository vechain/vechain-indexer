package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.VoteAggregate

@Profile("vevote-events")
@Repository
interface VoteAggregateRepository : BaseIndexedRepository<VoteAggregate> {
    // Find a specific aggregate by proposalId and choice
    fun findByProposalIdAndChoice(proposalId: String, choice: Int): VoteAggregate?
}
