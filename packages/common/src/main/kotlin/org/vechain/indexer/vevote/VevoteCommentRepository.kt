package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("vevote-comments")
@Repository
interface VevoteCommentRepository :
    BasePagingAndSortingIndexedRepository<VeVoteProposalComment, String> {
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VeVoteProposalComment>

    fun findAllByVoter(voter: String, pageable: Pageable): Slice<VeVoteProposalComment>

    fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    fun findAllBySupport(support: Support, pageable: Pageable): Slice<VeVoteProposalComment>

    fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>
}
