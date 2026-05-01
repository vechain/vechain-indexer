package org.vechain.indexer.safe.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.safe.SafeTxProposal

@Profile("safe")
interface SafeTxProposalRepository : BaseIndexedRepository<SafeTxProposal, String> {

    /** All proposals for a given Safe, paginated. */
    fun findBySafe(safe: String, pageable: Pageable): Slice<SafeTxProposal>
}
