package org.vechain.indexer.b3tr.proposal.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.proposal.ProposalComment
import org.vechain.indexer.b3tr.voting.Support

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Repository
interface ProposalCommentRepository : BaseIndexedRepository<ProposalComment, String> {
    @Query("{ 'proposalId': ?0 }")
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<ProposalComment>

    @Query("{ 'proposalId': ?0, 'support': ?1 }")
    fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>

    @Query("{ 'proposalId': ?0, 'voter': ?1 }")
    fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<ProposalComment>

    @Query("{ 'proposalId': ?0, 'voter': ?1, 'support': ?2 }")
    fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>

    @Query("{ 'voter': ?0 }")
    fun findAllByVoter(voter: String, pageable: Pageable): Slice<ProposalComment>

    @Query("{ 'voter': ?0, 'support': ?1 }")
    fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment>
}
