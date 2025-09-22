package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("vevote", "vevote-historic-proposals")
@Repository
interface HistoricProposalsRepository : BaseIndexedRepository<HistoricProposals, String> {

    fun findAll(pageable: Pageable): Slice<HistoricProposals>

    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricProposals>
}
