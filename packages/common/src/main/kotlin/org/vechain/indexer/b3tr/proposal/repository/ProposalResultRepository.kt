package org.vechain.indexer.b3tr.proposal.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.proposal.ProposalResult
import org.vechain.indexer.b3tr.proposal.ProposalState

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Repository
interface ProposalResultRepository : BaseIndexedRepository<ProposalResult, String> {
    @Query("{ 'proposalId': ?0 }") fun findByProposalId(proposalId: String): List<ProposalResult>

    /**
     * Find proposals where state is in the given list. Usage:
     * findByStateIn(listOf(ProposalState.Pending, ProposalState.Active))
     */
    @Query("{ 'state': { '\$in': ?0 } }")
    fun findByStateIn(states: List<ProposalState>): List<ProposalResult>

    /**
     * Find proposals where state is in the given list, paginated. Usage:
     * findByStateIn(listOf(ProposalState.Pending, ProposalState.Active), pageable)
     */
    @Query("{ 'state': { '\$in': ?0 } }")
    fun findByStateIn(
        states: List<ProposalState>,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Slice<ProposalResult>
}
