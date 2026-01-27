package org.vechain.indexer.vevote

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface VeVoteProposalResultRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<VeVoteProposalResult>, existing: List<VeVoteProposalResult>)

    fun findById(id: String): VeVoteProposalResult?

    fun findByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult>

    //  Get all aggregates for a proposal
    fun findAllByProposalId(proposalId: String, pageable: Pageable): Slice<VeVoteProposalResult>

    // Get all aggregates for a specific support across proposals
    fun findAllBySupport(support: Support, pageable: Pageable): Slice<VeVoteProposalResult>
}
