package org.vechain.indexer.b3tr.voting.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.voting.ProposalResult

@Profile("b3tr", "b3tr-voting", "b3tr-proposal-results")
@Repository
interface ProposalResultRepository : BasePagingAndSortingIndexedRepository<ProposalResult, String> {
    fun findByProposalId(proposalId: String): List<ProposalResult>
}
