package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.vevote.VevoteProposalComment

@Profile("vevote-comments")
@Repository
interface VevoteCommentRepository : BaseIndexedRepository<VevoteProposalComment> {
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VevoteProposalComment>

    fun findAllByVoter(voter: String, pageable: Pageable): Slice<VevoteProposalComment>

    fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VevoteProposalComment>

    fun findAllByChoicesContaining(choice: Int, pageable: Pageable): Slice<VevoteProposalComment>

    fun findAllByProposalIdAndChoicesContaining(
        proposalId: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment>

    fun findAllByVoterAndChoicesContaining(
        voter: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment>

    fun findAllByProposalIdAndVoterAndChoicesContaining(
        proposalId: String,
        voter: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment>
}
