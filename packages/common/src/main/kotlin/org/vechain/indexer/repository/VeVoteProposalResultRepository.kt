package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.vevote.VeVoteProposalResults

@Profile("vevote-results")
@Repository
interface VeVoteProposalResultRepository :
    BasePagingAndSortingIndexedRepository<VeVoteProposalResults, String> {
    fun findByProposalIdAndChoice(
        proposalId: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults>

    //  Get all aggregates for a proposal
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VeVoteProposalResults>

    // Get all aggregates for a specific choice across proposals
    fun findAllByChoice(choice: Int, pageable: Pageable): Slice<VeVoteProposalResults>
}
