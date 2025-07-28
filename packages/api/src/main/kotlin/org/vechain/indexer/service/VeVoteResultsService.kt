package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.vevote.Support
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository

@Profile("vevote-results")
@Service
open class VeVoteResultsService(
    private val vevoteProposalResultRepository: VeVoteProposalResultRepository
) {
    open fun getResultsByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findByProposalIdAndSupport(proposalId, support, pageable)

    open fun getResultsByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findAllByProposalId(proposalId, pageable)

    open fun getResultsBySupport(
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findAllBySupport(support, pageable)
}
