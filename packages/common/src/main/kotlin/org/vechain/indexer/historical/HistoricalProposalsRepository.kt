package org.vechain.indexer.historical

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("historical-proposals")
@Repository
interface HistoricalProposalsRepository : BaseIndexedRepository<HistoricalProposals, String> {

    fun findAll(pageable: Pageable): Slice<HistoricalProposals>

    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricalProposals>
}
