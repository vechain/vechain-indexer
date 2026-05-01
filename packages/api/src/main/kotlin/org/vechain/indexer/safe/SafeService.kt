package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.safe.repository.SafeMembershipRepository
import org.vechain.indexer.safe.repository.SafeTxProposalRepository
import org.vechain.indexer.safe.repository.SafeTxStateRepository
import org.vechain.indexer.thor.HexUtils

/**
 * Read-only service backing the Safe API. Each public method maps directly to a single Mongo lookup
 * (one API call = one DB query, per AGENTS.md). No cross-collection joins.
 */
@Profile("safe")
@Service
open class SafeService(
    private val membershipRepository: SafeMembershipRepository,
    private val txStateRepository: SafeTxStateRepository,
    private val proposalRepository: SafeTxProposalRepository,
) {

    /** All memberships for an owner address, filtered by `scope`. */
    open fun getSafesForOwner(
        owner: String,
        scope: SafeMembershipScope,
        pageable: Pageable,
    ): Slice<SafeMembership> {
        val ownerNorm = HexUtils.normalise(owner)
        return when (scope) {
            SafeMembershipScope.ALL -> membershipRepository.findByOwner(ownerNorm, pageable)
            SafeMembershipScope.CURRENT ->
                membershipRepository.findByOwnerAndRemovedBlockIsNull(ownerNorm, pageable)
            SafeMembershipScope.PAST ->
                membershipRepository.findByOwnerAndRemovedBlockIsNotNull(ownerNorm, pageable)
        }
    }

    /** Paginated proposals for a Safe, sourced from the SafeEmitter indexer. */
    open fun listProposals(safe: String, pageable: Pageable): Slice<SafeTxProposal> {
        return proposalRepository.findBySafe(HexUtils.normalise(safe), pageable)
    }

    /**
     * Single (safe, txHash) lookup. Returns an empty placeholder document when the (safe, txHash)
     * has not been observed yet, so the dapp can render the same shape without falling back to RPC.
     */
    open fun getTxState(safe: String, txHash: String): SafeTxState {
        val safeNorm = HexUtils.normalise(safe)
        val txHashNorm = HexUtils.normalise(txHash)
        val id = SafeTxState.buildId(safeNorm, txHashNorm)
        return txStateRepository.findById(id).orElseGet {
            SafeTxState(
                id = id,
                safe = safeNorm,
                txHash = txHashNorm,
                approvers = mutableListOf(),
                blockId = "",
                blockNumber = 0L,
                blockTimestamp = 0L,
                version = 0,
            )
        }
    }
}
