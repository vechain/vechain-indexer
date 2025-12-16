package org.vechain.indexer.stargate.nftHolders

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.stargate.token.TokenLevel

/**
 * Tracks the number of NFTs held by each owner address. Used to determine unique holder counts - an
 * address is a holder if total > 0.
 */
@Document(collection = "stargate_nft_owner_balances")
data class NftOwnerBalance(
    @Id val owner: String,
    val total: Long,
    val byLevel: Map<TokenLevel, Long>,
)
