package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.Views

@Document("nfts")
@JsonView(Views.Public::class)
data class IndexedNft
@ConstructorBinding
constructor(
    /** sha1 hash of `${contractAddress}-${tokenId}` */
    @Id val id: String,
    @JsonView(Views.Internal::class) override val version: Int,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : VersionedDocument {

    @JsonIgnore
    override fun getDocumentId(): String {
        return id
    }
}

@Document("nft_archives")
@JsonView(Views.Public::class)
data class NftArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: IndexedNft) : Archive<IndexedNft>
