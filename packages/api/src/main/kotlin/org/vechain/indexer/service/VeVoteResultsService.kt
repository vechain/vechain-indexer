package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository

@Profile("vevote-results")
@Service
open class VeVoteResultsService(
    private val vevoteProposalResultRepository: VeVoteProposalResultRepository
) {
    open fun getResultsByProposalIdAndChoice(
        proposalId: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findByProposalIdAndChoice(proposalId, choice, pageable)

    open fun getResultsByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findAllByProposalId(proposalId, pageable)

    open fun getResultsByChoice(choice: Int, pageable: Pageable): Slice<VeVoteProposalResults> =
        vevoteProposalResultRepository.findAllByChoice(choice, pageable)
}
