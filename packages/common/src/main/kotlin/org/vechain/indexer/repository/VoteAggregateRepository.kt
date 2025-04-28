package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.model.VoteAggregate

@Profile("vevote-events")
@Repository
interface VoteAggregateRepository : BaseIndexedRepository<VevoteProposalComment> {
    // Find a specific aggregate by proposalId and choice
    fun findByProposalIdAndChoice(proposalId: String, choice: Int): VoteAggregate?

    // Find all aggregates for a specific proposal
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VoteAggregate>

    // Find the top choices by weight for a specific proposal
    fun findAllByProposalIdOrderByTotalWeightDesc(
        proposalId: String,
        pageable: Pageable
    ): Slice<VoteAggregate>
}
