package org.vechain.indexer.b3tr.proposal.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.proposal.ProposalComment
import org.vechain.indexer.b3tr.voting.Support

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Repository
interface ProposalCommentRepository :
    BasePagingAndSortingIndexedRepository<ProposalComment, String> {
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<ProposalComment>

    fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>

    fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<ProposalComment>

    fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>

    fun findAllByVoter(voter: String, pageable: Pageable): Slice<ProposalComment>

    fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>
}
