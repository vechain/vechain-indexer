package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.utils.HexUtils

@Profile("vevote-events")
@Service
open class VevoteService(
    private val vevoteCommentRepository: VevoteCommentRepository,
) : IndexerService {
    /** Get comments for a specific proposal. */
    open fun getCommentsByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByProposalId(proposalId, pageable)

    /** Get comments by a specific voter. */
    open fun getCommentsByVoter(
        voter: String,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =

        /** Get comments for a specific proposal and voter. */
        open

    fun getCommentsByProposalAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByProposalIdAndVoter(
            proposalId,
            HexUtils.normalise(voter),
            pageable,
        )

    /** Get comments that contain a specific choice. */
    open fun getCommentsByChoice(
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByChoicesContaining(choice, pageable)

    /** Get comments for a specific proposal that contain a specific choice. */
    open fun getCommentsByProposalAndChoice(
        proposalId: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByProposalIdAndChoicesContaining(
            proposalId,
            choice,
            pageable,
        )

    /** Get comments by a specific voter that contain a specific choice. */
    open fun getCommentsByVoterAndChoice(
        voter: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByVoterAndChoicesContaining(
            HexUtils.normalise(voter),
            choice,
            pageable,
        )

    /** Get comments for a specific proposal and voter that contain a specific choice. */
    open fun getCommentsByProposalAndVoterAndChoice(
        proposalId: String,
        voter: String,
        choice: Int,
        pageable: Pageable,
    ): Slice<VevoteProposalComment> =
        vevoteCommentRepository.findAllByProposalIdAndVoterAndChoicesContaining(
            proposalId,
            HexUtils.normalise(voter),
            choice,
            pageable,
        )

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "VevoteProposalComment" to
                (vevoteCommentRepository.getLatestRecord()?.blockNumber ?: 0),
        )
}
