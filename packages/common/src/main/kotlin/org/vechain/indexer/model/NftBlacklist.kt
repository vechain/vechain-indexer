package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.Views

@Document("nft_blacklist")
@JsonView(Views.Public::class)
data class NftBlacklist
@ConstructorBinding
constructor(
    @Id val contractAddress: String,
    @JsonView(Views.Internal::class) override val version: Int,
    val isBlacklisted: Boolean,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : VersionedDocument {

    @JsonIgnore
    override fun getDocumentId(): String {
        return contractAddress
    }
}

@Document("nft_blacklist_archives")
@JsonView(Views.Public::class)
data class NftBlacklistArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: NftBlacklist) : Archive<NftBlacklist>
