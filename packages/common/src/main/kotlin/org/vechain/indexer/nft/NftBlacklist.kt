package org.vechain.indexer.nft

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.Views

/** Current blacklist state of one NFT collection, keyed by its normalised contract address. */
@Document(collection = IndexerNames.NFT_BLACKLIST.COLLECTION)
@JsonView(Views.Public::class)
data class NftBlacklist(
    @Id val id: String,
    val isBlacklisted: Boolean,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        const val IS_BLACKLISTED_FIELD = "isBlacklisted"

        fun buildId(contractAddress: String): String = HexUtils.normalise(contractAddress)
    }
}
