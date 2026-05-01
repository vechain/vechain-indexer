package org.vechain.indexer.safe.response

import org.vechain.indexer.safe.SafeSubcall
import org.vechain.indexer.safe.SafeTxProposal

data class SafeSubcallResponse(
    val target: String,
    val value: String,
    val data: String,
    val operation: Int,
    val label: String,
) {
    companion object {
        fun from(doc: SafeSubcall): SafeSubcallResponse =
            SafeSubcallResponse(
                target = doc.target,
                value = doc.value.toString(),
                data = doc.data,
                operation = doc.operation,
                label = doc.label,
            )
    }
}

/**
 * API representation of a Safe transaction proposal sourced from the SafeEmitter contract. The dapp
 * lists these by safe to render the activity feed; per-tx approval/execution status is fetched
 * separately via `GET /safes/{safe}/transactions/{txHash}/state`.
 *
 * `uint256` values are serialised as decimal strings to avoid JSON numeric overflow.
 */
data class SafeProposalResponse(
    val safe: String,
    val txHash: String,
    val proposer: String?,
    val proposedBlock: Long?,
    val proposedTimestamp: Long?,
    val proposedVechainTxId: String?,
    val to: String?,
    val value: String?,
    val data: String?,
    val operation: Int?,
    val nonce: String?,
    val description: String?,
    val safeTxGas: String?,
    val baseGas: String?,
    val gasPrice: String?,
    val gasToken: String?,
    val refundReceiver: String?,
    val subcalls: List<SafeSubcallResponse>?,
    val lastEventBlock: Long,
) {
    companion object {
        fun from(doc: SafeTxProposal): SafeProposalResponse =
            SafeProposalResponse(
                safe = doc.safe,
                txHash = doc.txHash,
                proposer = doc.proposer,
                proposedBlock = doc.proposedBlock,
                proposedTimestamp = doc.proposedTimestamp,
                proposedVechainTxId = doc.proposedVechainTxId,
                to = doc.to,
                value = doc.value?.toString(),
                data = doc.data,
                operation = doc.operation,
                nonce = doc.nonce?.toString(),
                description = doc.description,
                safeTxGas = doc.safeTxGas?.toString(),
                baseGas = doc.baseGas?.toString(),
                gasPrice = doc.gasPrice?.toString(),
                gasToken = doc.gasToken,
                refundReceiver = doc.refundReceiver,
                subcalls = doc.subcalls?.map { SafeSubcallResponse.from(it) },
                lastEventBlock = doc.blockNumber,
            )
    }
}
