package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.HistoricalProposals
import org.vechain.indexer.repository.HistoricalProposalsRepository

@Profile("historical-proposals")
@Service
open class HistoricalApiService(
    private val historicalProposalsRepository: HistoricalProposalsRepository
) {

    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricalProposals> =
        historicalProposalsRepository.findByProposalId(proposalId, pageable)
}
