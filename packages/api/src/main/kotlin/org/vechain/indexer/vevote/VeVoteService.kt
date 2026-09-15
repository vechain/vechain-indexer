package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("vevote")
@Service
open class VeVoteService(private val repository: VeVoteCommentReadRepository) {
    /** Comments, narrowed by any of proposal, voter and support. */
    open fun getComments(
        proposalId: String?,
        voter: String?,
        support: Support?,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> =
        offsetSlice(pageable, VeVoteProposalComment::blockNumber.name) { offset, limit, direction ->
            repository.find(proposalId, voter, support, offset, limit, direction)
        }
}
