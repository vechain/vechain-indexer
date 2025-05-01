package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.VoteAggregate

@Profile("vevote-result")
@Repository
interface VoteAggregateRepository : BaseIndexedRepository<VoteAggregate> {

    fun findByProposalIdAndChoice(proposalId: String, choice: Int): Slice<VoteAggregate>

    //  Get all aggregates for a proposal
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VoteAggregate>

    // Get all aggregates for a specific choice across proposals
    fun findAllByChoice(choice: Int, pageable: Pageable): Slice<VoteAggregate>
}
