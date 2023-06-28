package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("nfts")
data class IndexedNFT
@ConstructorBinding
constructor(
    /** sha1 hash of `${contractAddress}-${tokenId}` */
    @Id val id: String,
    @JsonIgnore override val version: Int,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : VersionedDocument {
    override fun getDocumentId(): String {
        return id
    }
}
