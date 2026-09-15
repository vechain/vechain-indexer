package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("vevote")
@Service
open class VeVoteResultsService(private val repository: VeVoteResultReadRepository) {
    /** The current tally of each support of a proposal, or of one support across proposals. */
    open fun getResults(
        proposalId: String?,
        support: Support?,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult> =
        offsetSlice(pageable, VeVoteProposalResult::blockNumber.name) { offset, limit, direction ->
            repository.find(proposalId, support, offset, limit, direction)
        }
}
