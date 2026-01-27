package org.vechain.indexer.b3tr.proposal.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.proposal.ProposalComment
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface ProposalCommentRepository : PostgresIndexedRepository {
    // Write operations
    fun saveAll(comments: List<ProposalComment>): List<ProposalComment>

    // Query operations
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

    /** Returns the latest record (by block number) from the repository, or null if empty. */
    fun getLatestRecord(): ProposalComment?
}
