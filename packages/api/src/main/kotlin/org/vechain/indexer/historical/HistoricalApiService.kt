package org.vechain.indexer.historical

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service

@Profile("historical-proposals")
@Service
open class HistoricalApiService(
    private val historicalProposalsRepository: HistoricalProposalsRepository
) {
    fun findAll(proposalId: String?, pageable: Pageable): Slice<HistoricalProposals> =
        if (proposalId != null) {
            historicalProposalsRepository.findByProposalId(proposalId, pageable)
        } else {
            historicalProposalsRepository.findAll(pageable)
        }
}
