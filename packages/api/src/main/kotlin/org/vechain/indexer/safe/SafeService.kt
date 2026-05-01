package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.safe.repository.SafeMembershipRepository
import org.vechain.indexer.safe.repository.SafeTxProposalRepository
import org.vechain.indexer.safe.repository.SafeTxStateRepository
import org.vechain.indexer.safe.response.SafeMembershipResponse
import org.vechain.indexer.safe.response.SafeProposalResponse
import org.vechain.indexer.safe.response.SafeTxStateResponse
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
    ): Slice<SafeMembershipResponse> {
        val ownerNorm = HexUtils.normalise(owner)
        val raw =
            when (scope) {
                SafeMembershipScope.ALL -> membershipRepository.findByOwner(ownerNorm, pageable)
                SafeMembershipScope.CURRENT ->
                    membershipRepository.findByOwnerAndRemovedBlockIsNull(ownerNorm, pageable)
                SafeMembershipScope.PAST ->
                    membershipRepository.findByOwnerAndRemovedBlockIsNotNull(ownerNorm, pageable)
            }
        return raw.map { SafeMembershipResponse.from(it) }
    }

    /** Paginated proposals for a Safe, sourced from the SafeEmitter indexer. */
    open fun listProposals(safe: String, pageable: Pageable): Slice<SafeProposalResponse> {
        val safeNorm = HexUtils.normalise(safe)
        return proposalRepository.findBySafe(safeNorm, pageable).map {
            SafeProposalResponse.from(it)
        }
    }

    /**
     * Single (safe, txHash) lookup. Returns an "empty" placeholder response when the document does
     * not exist, so the dapp can render the same shape for an un-approved tx without falling back
     * to RPC.
     */
    open fun getTxState(safe: String, txHash: String): SafeTxStateResponse {
        val id = SafeTxState.buildId(safe, txHash)
        return txStateRepository
            .findById(id)
            .map { SafeTxStateResponse.from(it) }
            .orElseGet { SafeTxStateResponse.empty(safe, txHash) }
    }
}
