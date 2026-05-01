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

/**
 * Registry of Safes deployed via the canonical `SafeProxyFactory` for the network. The presence of
 * a document here is the trust signal used by the membership, tx-state and tx-proposal indexers to
 * filter out events whose `address` (or indexed `safe` param) is not a real Safe.
 *
 * One document per proxy address.
 */
@Document(collection = IndexerNames.SAFE_PROXY.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SafeProxy(
    @Id val id: String,
    val singleton: String,
    val createdBlock: Long,
    val createdTimestamp: Long,
    val vechainTxId: String,
    @JsonIgnore override var blockId: String,
    @JsonIgnore override var blockNumber: Long,
    @JsonIgnore override var blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun buildId(proxy: String): String = HexUtils.normalise(proxy)
    }
}
