package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Views

/** Decoded subcall metadata for a single entry in a `SafeBatchTxProposed` event. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeSubcall(
    val target: String,
    val value: BigInteger,
    val data: String,
    val operation: Int,
    val label: String,
)

/** A transaction proposed through the SafeEmitter, identified by its indexed `safe` param. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeTxProposal(
    val id: String,
    val safe: String,
    val txHash: String,
    val proposer: String? = null,
    val proposedBlock: Long? = null,
    val proposedTimestamp: Long? = null,
    val proposedVechainTxId: String? = null,
    val to: String? = null,
    val value: BigInteger? = null,
    val data: String? = null,
    val operation: Int? = null,
    val nonce: BigInteger? = null,
    val description: String? = null,
    val safeTxGas: BigInteger? = null,
    val baseGas: BigInteger? = null,
    val gasPrice: BigInteger? = null,
    val gasToken: String? = null,
    val refundReceiver: String? = null,
    val subcalls: List<SafeSubcall>? = null,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
) {
    companion object {
        const val DESCRIPTION_MAX_LENGTH = 512

        fun buildId(safe: String, txHash: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(txHash)}"
    }
}
