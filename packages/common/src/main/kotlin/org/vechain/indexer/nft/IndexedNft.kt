package org.vechain.indexer.nft

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@Document(IndexerNames.NFT.COLLECTION)
@JsonView(Views.Public::class)
data class IndexedNft
@ConstructorBinding
constructor(
    /** sha1 hash of `${contractAddress}-${tokenId}` */
    @Id val id: String,
    @field:JsonView(Views.Internal::class) override val version: Int,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
    @JsonIgnore var isBlacklisted: Boolean? = null,
) : VersionedDocument {

    @JsonIgnore
    override fun getDocumentId(): String {
        return id
    }
}
