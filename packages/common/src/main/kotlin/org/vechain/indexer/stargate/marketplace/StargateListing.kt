package org.vechain.indexer.stargate.marketplace

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.stargate.token.TokenLevel

/** Mirrors the `stargate_marketplace.status` enum. */
enum class ListingStatus {
    OPEN,
    SOLD,
    CANCELLED,
}

/** A listing's state after [blockNumber]. [paymentToken] is the zero address for VET. */
data class StargateListing(
    val saleId: BigInteger,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
    val txId: String,
    val tokenId: BigInteger,
    val seller: String,
    val price: BigInteger,
    val paymentToken: String,
    val status: ListingStatus,
    val buyer: String? = null,
) : IndexedDocument

/** A listing beside its token's current state; the token fields are null without a state row. */
data class ListedToken(
    val listing: StargateListing,
    val level: TokenLevel?,
    val vetStaked: BigInteger?,
    val owner: String?,
)

/** The orderings of the listings page. */
enum class ListingSort {
    NEWEST,
    PRICE,
}
