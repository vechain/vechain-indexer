package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

/** Read-only service backing the Safe API; one endpoint is one query. */
@Profile("safe")
@Service
open class SafeService(private val repository: SafeReadRepository) : IndexerService {

    /** All memberships for an owner address, filtered by `scope`. */
    open fun getSafesForOwner(
        owner: String,
        scope: SafeMembershipScope,
        pageable: Pageable,
    ): Slice<SafeMembership> =
        offsetSlice(pageable, SafeMembership::addedBlock.name) { offset, limit, direction ->
            repository.findMembershipsByOwner(
                HexUtils.normalise(owner),
                scope,
                offset,
                limit,
                direction,
            )
        }

    /** Paginated proposals for a Safe, sourced from the SafeEmitter events. */
    open fun listProposals(safe: String, pageable: Pageable): Slice<SafeTxProposal> =
        offsetSlice(pageable, SafeTxProposal::blockNumber.name) { offset, limit, direction ->
            repository.findProposalsBySafe(HexUtils.normalise(safe), offset, limit, direction)
        }

    /** An unseen (safe, txHash) reads back empty, so the dapp never falls back to RPC. */
    open fun getTxState(safe: String, txHash: String): SafeTxState {
        val safeNorm = HexUtils.normalise(safe)
        val txHashNorm = HexUtils.normalise(txHash)
        return repository.findTxState(safeNorm, txHashNorm)
            ?: SafeTxState(
                id = SafeTxState.buildId(safeNorm, txHashNorm),
                safe = safeNorm,
                txHash = txHashNorm,
                blockId = "",
                blockNumber = 0L,
                blockTimestamp = 0L,
            )
    }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("Safe" to repository.latestBlockNumber())
}
