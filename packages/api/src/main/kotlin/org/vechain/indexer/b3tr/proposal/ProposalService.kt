package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("b3tr", "b3tr-proposal")
@Service
open class ProposalService(
    private val resultRepository: ProposalResultReadRepository,
    private val commentRepository: ProposalCommentReadRepository,
) : IndexerService {

    /**
     * Get the results of a proposal.
     *
     * @param proposalId Proposal ID to filter by.
     */
    open fun getProposalResult(proposalId: String): ProposalResult? =
        resultRepository.findByProposalId(proposalId)

    /**
     * Get proposal results paginated, optionally narrowed to a set of states.
     *
     * @param states States to filter by; every state when empty.
     * @param pageable Pagination information.
     */
    open fun getAllProposalResults(
        states: List<ProposalState>,
        pageable: Pageable,
    ): Slice<ProposalResult> =
        offsetSlice(pageable, ProposalResult::createdAtBlockNumber.name) { offset, limit, direction
            ->
            resultRepository.find(states, offset, limit, direction)
        }

    /**
     * Get comments, narrowed by any of proposal, voter and support.
     *
     * @param proposalId Proposal ID to filter by.
     * @param voter Voter to filter by.
     * @param support Support to filter by.
     */
    open fun getComments(
        proposalId: String?,
        voter: String?,
        support: Support?,
        pageable: Pageable,
    ): Slice<ProposalComment> =
        offsetSlice(pageable, ProposalComment::blockNumber.name) { offset, limit, direction ->
            commentRepository.find(proposalId, voter, support, offset, limit, direction)
        }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "ProposalResult" to resultRepository.latestBlockNumber(),
            "ProposalComment" to commentRepository.latestBlockNumber(),
        )
}
