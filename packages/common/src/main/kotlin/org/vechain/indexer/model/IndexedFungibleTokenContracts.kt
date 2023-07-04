package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("fungible_token_contracts")
data class IndexedFungibleTokenContracts
@ConstructorBinding
constructor(

    /** The address of the tokens owner */
    @Id val tokenOwner: String,
    val tokenAddresses: SortedSet<String>,
    @JsonIgnore override val version: Int,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : VersionedDocument {
    override fun getDocumentId(): String {
        return tokenOwner
    }
}
