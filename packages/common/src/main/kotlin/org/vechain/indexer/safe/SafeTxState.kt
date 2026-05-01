package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
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

/**
 * Aggregated state of a Safe transaction (identified by Safe address + Safe `txHash`). One document
 * per (safe, txHash). Approvers are appended as `ApproveHash` events arrive; the execution status
 * is updated on `ExecutionSuccess` / `ExecutionFailure`.
 */
@Document(collection = IndexerNames.SAFE_TX_STATE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeTxState(
    @Id val id: String,
    val safe: String,
    val txHash: String,
    var approvers: MutableList<SafeTxApproval> = mutableListOf(),
    var executed: Boolean = false,
    var executor: String? = null,
    var executedBlock: Long? = null,
    var executedTimestamp: Long? = null,
    var vechainTxId: String? = null,
    var failed: Boolean = false,
    @JsonIgnore override var blockId: String,
    @JsonIgnore override var blockNumber: Long,
    @JsonIgnore override var blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun buildId(safe: String, txHash: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(txHash)}"
    }
}
