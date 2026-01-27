package org.vechain.indexer.b3tr.proposal.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.proposal.ProposalResult
import org.vechain.indexer.b3tr.proposal.ProposalState
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface ProposalResultRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(updated: List<ProposalResult>, existing: List<ProposalResult>)

    // Query operations
    fun findById(proposalId: String): ProposalResult?

    fun findAll(pageable: Pageable): Slice<ProposalResult>

    /**
     * Find proposals where state is in the given list. Usage:
     * findByStateIn(listOf(ProposalState.Pending, ProposalState.Active))
     */
    fun findByStateIn(states: List<ProposalState>): List<ProposalResult>

    /**
     * Find proposals where state is in the given list, paginated. Usage:
     * findByStateIn(listOf(ProposalState.Pending, ProposalState.Active), pageable)
     */
    fun findByStateIn(states: List<ProposalState>, pageable: Pageable): Slice<ProposalResult>

    /** Returns the latest record (by block number) from the repository, or null if empty. */
    fun getLatestRecord(): ProposalResult?
}
