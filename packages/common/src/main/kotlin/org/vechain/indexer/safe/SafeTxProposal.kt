package org.vechain.indexer.safe

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
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

/**
 * One document per `(safe, txHash)`, populated by the SafeEmitter contract's events:
 * - `SafeTxProposed` sets envelope fields and the `proposer` (also `proposed*` block metadata).
 * - `SafeTxHashFields` sets the gas-related fields not carried in the primary event.
 * - `SafeBatchTxProposed` populates `subcalls` for batched proposals.
 *
 * Identity comes from the indexed `safe` and `txHash` event params, not `event.address` (which is
 * the emitter contract).
 */
@Document(collection = IndexerNames.SAFE_TX_PROPOSAL.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeTxProposal(
    @Id val id: String,
    val safe: String,
    val txHash: String,
    var proposer: String? = null,
    var proposedBlock: Long? = null,
    var proposedTimestamp: Long? = null,
    var proposedVechainTxId: String? = null,
    var to: String? = null,
    var value: BigInteger? = null,
    var data: String? = null,
    var operation: Int? = null,
    var nonce: BigInteger? = null,
    var description: String? = null,
    @JsonIgnore var envelopeRecorded: Boolean = false,
    var safeTxGas: BigInteger? = null,
    var baseGas: BigInteger? = null,
    var gasPrice: BigInteger? = null,
    var gasToken: String? = null,
    var refundReceiver: String? = null,
    @JsonIgnore var hashFieldsRecorded: Boolean = false,
    var subcalls: List<SafeSubcall>? = null,
    @JsonIgnore override var blockId: String,
    @JsonIgnore override var blockNumber: Long,
    @JsonIgnore override var blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        const val DESCRIPTION_MAX_LENGTH = 512

        fun buildId(safe: String, txHash: String): String =
            "${HexUtils.normalise(safe)}_${HexUtils.normalise(txHash)}"
    }
}
