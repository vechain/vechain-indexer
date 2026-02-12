package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("vevote", "vevote-results")
@Repository
interface VeVoteProposalResultRepository : BaseIndexedRepository<VeVoteProposalResult, String> {
    @Query("{ 'proposalId': ?0, 'support': ?1 }")
    fun findByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult>

    //  Get all aggregates for a proposal
    @Query("{ 'proposalId': ?0 }")
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VeVoteProposalResult>

    // Get all aggregates for a specific support across proposals
    @Query("{ 'support': ?0 }")
    fun findAllBySupport(support: Support, pageable: Pageable): Slice<VeVoteProposalResult>
}
