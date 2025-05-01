package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.VoteAggregate
import org.vechain.indexer.repository.VoteAggregateRepository

@Profile("vevote-result")
@Service
open class VevoteResultsService(private val vevoteAggregateRepository: VoteAggregateRepository) {

    open fun getResultsByProposalIdAndChoice(
        proposalId: String,
        choice: Int,
        pageable: Pageable
    ): Slice<VoteAggregate> =
        vevoteAggregateRepository.findByProposalIdAndChoice(proposalId, choice)

    open fun getResultsByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VoteAggregate> = vevoteAggregateRepository.findAllByProposalId(proposalId, pageable)

    open fun getResultsByChoice(
        choice: Int,
        pageable: Pageable,
    ): Slice<VoteAggregate> = vevoteAggregateRepository.findAllByChoice(choice, pageable)
}
