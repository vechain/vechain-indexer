package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.vevote.Support
import org.vechain.indexer.model.vevote.VeVoteProposalComment
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.utils.HexUtils

@Profile("vevote-comments")
@Service
open class VeVoteService(private val vevoteCommentRepository: VevoteCommentRepository) {
    /** Get comments for a specific proposal. */
    open fun getCommentsByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> =
        vevoteCommentRepository.findAllByProposalId(proposalId, pageable)

    /** Get comments by a specific voter. */
    open fun getCommentsByVoter(voter: String, pageable: Pageable): Slice<VeVoteProposalComment> =
        vevoteCommentRepository.findAllByVoter(HexUtils.normalise(voter), pageable)

    /** Get comments for a specific proposal and voter. */
    open fun getCommentsByProposalAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> =
        vevoteCommentRepository.findAllByProposalIdAndVoter(
            proposalId,
            HexUtils.normalise(voter),
            pageable,
        )

    /** Get comments that contain a specific support. */
    open fun getCommentsBySupport(
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> = vevoteCommentRepository.findAllBySupport(support, pageable)

    /** Get comments for a specific proposal that contain a specific support. */
    open fun getCommentsByProposalAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        println(support)
        println(proposalId)
        return vevoteCommentRepository.findAllByProposalIdAndSupport(proposalId, support, pageable)
    }

    /** Get comments by a specific voter that contain a specific support. */
    open fun getCommentsByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> =
        vevoteCommentRepository.findAllByVoterAndSupport(
            HexUtils.normalise(voter),
            support,
            pageable,
        )

    /** Get comments for a specific proposal and voter that contain a specific support. */
    open fun getCommentsByProposalAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> =
        vevoteCommentRepository.findAllByProposalIdAndVoterAndSupport(
            proposalId,
            HexUtils.normalise(voter),
            support,
            pageable,
        )
}
