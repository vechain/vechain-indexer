package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.HistoricalProposals

@Profile("historical-proposals")
@Repository
interface HistoricalProposalsRepository : BaseIndexedRepository<HistoricalProposals, String> {

    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricalProposals>
}
