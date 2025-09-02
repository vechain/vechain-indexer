package org.vechain.indexer.b3tr.voting

import kotlin.to
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.voting.repository.ProposalCommentRepository
import org.vechain.indexer.b3tr.voting.repository.ProposalResultRepository
import org.vechain.indexer.b3tr.voting.repository.XAllocResultRepository
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-voting")
@Service
open class VotingService(
    private val xallocResultRepository: XAllocResultRepository,
    private val proposalResultRepository: ProposalResultRepository,
    private val proposalCommentRepository: ProposalCommentRepository,
) : IndexerService {
    /**
     * Get the results of XAllocation voting for a specific round.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResults(roundId: Int): List<XAllocResult> =
        xallocResultRepository.findByRoundId(roundId)

    /**
     * Get the results of a proposal.
     *
     * @param proposalId Proposal ID to filter by.
     */
    open fun getProposalResult(proposalId: String): List<ProposalResult> =
        proposalResultRepository.findByProposalId(proposalId)

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
            "XAllocResult" to (xallocResultRepository.getLatestRecord()?.blockNumber ?: 0),
            "ProposalResult" to (proposalResultRepository.getLatestRecord()?.blockNumber ?: 0),
            "ProposalComment" to (proposalCommentRepository.getLatestRecord()?.blockNumber ?: 0),
        )
}
