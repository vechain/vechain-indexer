package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("vevote", "vevote-comments")
@Repository
interface VevoteCommentRepository : BaseIndexedRepository<VeVoteProposalComment, String> {
    @Query("{ 'proposalId': ?0 }")
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VeVoteProposalComment>

    @Query("{ 'voter': ?0 }")
    fun findAllByVoter(voter: String, pageable: Pageable): Slice<VeVoteProposalComment>

    @Query("{ 'proposalId': ?0, 'voter': ?1 }")
    fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    @Query("{ 'support': ?0 }")
    fun findAllBySupport(support: Support, pageable: Pageable): Slice<VeVoteProposalComment>

    @Query("{ 'proposalId': ?0, 'support': ?1 }")
    fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    @Query("{ 'voter': ?0, 'support': ?1 }")
    fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>

    @Query("{ 'proposalId': ?0, 'voter': ?1, 'support': ?2 }")
    fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment>
}
