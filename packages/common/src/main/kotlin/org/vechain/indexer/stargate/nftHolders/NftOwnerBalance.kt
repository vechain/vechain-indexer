package org.vechain.indexer.stargate.nftHolders

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.stargate.token.TokenLevel

/**
 * Tracks the number of NFTs held by each owner address. Used to determine unique holder counts - an
 * address is a holder if total > 0.
 */
data class NftOwnerBalance(
    @JsonIgnore val owner: String,
    val total: Long,
    val byLevel: Map<TokenLevel, Long>,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore override val version: Int = 1,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = owner
}
