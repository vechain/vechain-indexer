package org.vechain.indexer.b3tr.proposal

import kotlin.to
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.proposal.repository.ProposalCommentRepository
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-proposal")
@Service
open class ProposalService(
    private val proposalResultRepository: ProposalResultRepository,
    private val proposalCommentRepository: ProposalCommentRepository,
) : IndexerService {

    /**
     * Get the results of a proposal.
     *
     * @param proposalId Proposal ID to filter by.
     */
    open fun getProposalResult(proposalId: String): ProposalResult? =
        proposalResultRepository.findById(proposalId).orElse(null)

    /**
     * Get all proposal results paginated.
     *
     * @param pageable Pagination information.
     */
    fun getAllProposalResults(pageable: Pageable): Slice<ProposalResult> =
        proposalResultRepository.findAll(pageable)

    /**
     * Get comments for a proposal.
     *
     * @param proposalId Proposal ID to filter by.
     */
    open fun getComments(
        proposalId: String,
        support: Support?,
        pageable: Pageable,
    ): Slice<ProposalComment> =
        if (support == null) {
            proposalCommentRepository.findAllByProposalId(proposalId, pageable)
        } else {
            proposalCommentRepository.findAllByProposalIdAndSupport(proposalId, support, pageable)
        }

    /**
     * Get comments for a proposal.
     *
     * @param proposalId Proposal ID to filter by.
     * @param voter Voter to filter by.
     */
    open fun getComments(
        proposalId: String,
        voter: String,
        support: Support?,
        pageable: Pageable,
    ): Slice<ProposalComment> =
        if (support == null) {
            proposalCommentRepository.findAllByProposalIdAndVoter(
                proposalId,
                HexUtils.normalise(voter),
                pageable,
            )
        } else {
            proposalCommentRepository.findAllByProposalIdAndVoterAndSupport(
                proposalId,
                HexUtils.normalise(voter),
                support,
                pageable,
            )
        }

    open fun getCommentsForVoter(
        voter: String,
        support: Support?,
        pageable: Pageable,
    ): Slice<ProposalComment> =
        if (support == null) {
            proposalCommentRepository.findAllByVoter(HexUtils.normalise(voter), pageable)
        } else {
            proposalCommentRepository.findAllByVoterAndSupport(
                HexUtils.normalise(voter),
                support,
                pageable,
            )
        }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "ProposalResult" to (proposalResultRepository.getLatestRecord()?.blockNumber ?: 0),
            "ProposalComment" to (proposalCommentRepository.getLatestRecord()?.blockNumber ?: 0),
        )
}
