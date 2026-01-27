package org.vechain.indexer.vevote

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface VevoteCommentRepository : PostgresIndexedRepository {
    fun saveAll(comments: List<VeVoteProposalComment>)

    fun existsById(id: String): Boolean

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
