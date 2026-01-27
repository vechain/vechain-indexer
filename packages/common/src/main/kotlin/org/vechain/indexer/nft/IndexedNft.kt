package org.vechain.indexer.nft

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
data class IndexedNft(
    /** sha1 hash of `${contractAddress}-${tokenId}` */
    @JsonIgnore val id: String,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore var isBlacklisted: Boolean? = null,
) : VersionedDocument {

    @JsonIgnore
    override fun getDocumentId(): String {
        return id
    }
}
