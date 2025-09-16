package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service

@Profile("vevote", "vevote-historic-proposals")
@Service
open class HistoricApiService(
    private val historicProposalsRepository: HistoricProposalsRepository
) {
    fun findAll(proposalId: String?, pageable: Pageable): Slice<HistoricProposals> =
        if (proposalId != null) {
            historicProposalsRepository.findByProposalId(proposalId, pageable)
        } else {
            historicProposalsRepository.findAll(pageable)
        }
}
