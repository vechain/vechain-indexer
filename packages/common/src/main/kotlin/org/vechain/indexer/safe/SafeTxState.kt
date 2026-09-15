package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Views

/** A single approval recorded by an owner of a Safe for a given transaction hash. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeTxApproval(
    val owner: String,
    val block: Long,
    val blockTimestamp: Long,
    val vechainTxId: String,
)

/** The approvals a Safe transaction has collected and whether it has run. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeTxState(
    val id: String,
    val safe: String,
    val txHash: String,
    val approvers: List<SafeTxApproval> = emptyList(),
    val executed: Boolean = false,
    val executor: String? = null,
    val executedBlock: Long? = null,
    val executedTimestamp: Long? = null,
    val vechainTxId: String? = null,
    val failed: Boolean = false,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
) {
    companion object {
        fun buildId(safe: String, txHash: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(txHash)}"
    }
}
