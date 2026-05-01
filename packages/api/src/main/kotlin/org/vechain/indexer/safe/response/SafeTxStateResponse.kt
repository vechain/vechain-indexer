package org.vechain.indexer.safe.response

import org.vechain.indexer.safe.SafeTxApproval
import org.vechain.indexer.safe.SafeTxState
import org.vechain.indexer.thor.HexUtils

data class SafeTxApprovalResponse(
    val owner: String,
    val block: Long,
    val blockTimestamp: Long,
    val vechainTxId: String,
) {
    companion object {
        fun from(doc: SafeTxApproval): SafeTxApprovalResponse =
            SafeTxApprovalResponse(
                owner = doc.owner,
                block = doc.block,
                blockTimestamp = doc.blockTimestamp,
                vechainTxId = doc.vechainTxId,
            )
    }
}

/**
 * API representation of an aggregated Safe transaction state. The dapp uses this to short-circuit
 * the per-owner `approvedHashes(owner, hash)` reads and `ExecutionSuccess` log filtering.
 */
data class SafeTxStateResponse(
    val safe: String,
    val txHash: String,
    val approvers: List<SafeTxApprovalResponse>,
    val executed: Boolean,
    val executor: String?,
    val executedBlock: Long?,
    val executedTimestamp: Long?,
    val vechainTxId: String?,
    val failed: Boolean,
    val lastEventBlock: Long,
) {
    companion object {
        fun from(doc: SafeTxState): SafeTxStateResponse =
            SafeTxStateResponse(
                safe = doc.safe,
                txHash = doc.txHash,
                approvers = doc.approvers.map { SafeTxApprovalResponse.from(it) },
                executed = doc.executed,
                executor = doc.executor,
                executedBlock = doc.executedBlock,
                executedTimestamp = doc.executedTimestamp,
                vechainTxId = doc.vechainTxId,
                failed = doc.failed,
                lastEventBlock = doc.blockNumber,
            )

        /** Empty response for a (safe, txHash) pair we've never observed an event for. */
        fun empty(safe: String, txHash: String): SafeTxStateResponse =
            SafeTxStateResponse(
                safe = HexUtils.normalise(safe),
                txHash = HexUtils.normalise(txHash),
                approvers = emptyList(),
                executed = false,
                executor = null,
                executedBlock = null,
                executedTimestamp = null,
                vechainTxId = null,
                failed = false,
                lastEventBlock = 0L,
            )
    }
}
