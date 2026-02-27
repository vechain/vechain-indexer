package org.vechain.indexer.stargate.nftHolders

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.stargate.token.TokenLevel

/**
 * Tracks the number of NFTs held by each owner address at a given block. One document per (owner,
 * blockNumber) — a changelog of balance changes. Used to determine unique holder counts — an
 * address is a holder if total > 0.
 */
@Document(collection = IndexerNames.NFT_OWNER_BALANCE.COLLECTION)
data class NftOwnerBalance(
    val owner: String,
    val total: Long,
    val byLevel: Map<TokenLevel, Long>,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @Id val id: String = "${owner}_${blockNumber}",
) : IndexedDocument
