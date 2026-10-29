package org.vechain.indexer.stargate.marketplace

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Views

/** An open listing as the Stargate app reads it; the contract remains the source of truth. */
@JsonView(Views.Public::class)
data class MarketplaceListing(
    val saleId: String,
    val tokenId: String,
    val seller: String,
    val price: BigInteger,
    val paymentToken: String,
    val listedAt: ListedAt,
    @JsonInclude(JsonInclude.Include.ALWAYS) val level: TokenLevel?,
    @JsonInclude(JsonInclude.Include.ALWAYS) val vetStaked: BigInteger?,
    val tokenExists: Boolean,
    val sellerIsOwner: Boolean,
) {
    @JsonView(Views.Public::class)
    data class ListedAt(val blockNumber: Long, val blockTimestamp: Long)

    companion object {
        /** A token without a state row, or burned to the zero address, no longer exists. */
        fun from(row: ListedToken): MarketplaceListing {
            val listing = row.listing
            val exists = row.owner != null && row.owner != Address.ZERO_ADDRESS
            return MarketplaceListing(
                saleId = listing.saleId.toString(),
                tokenId = listing.tokenId.toString(),
                seller = listing.seller,
                price = listing.price,
                paymentToken = listing.paymentToken,
                listedAt = ListedAt(listing.blockNumber, listing.blockTimestamp),
                level = row.level,
                vetStaked = row.vetStaked,
                tokenExists = exists,
                sellerIsOwner = exists && row.owner == listing.seller,
            )
        }
    }
}
