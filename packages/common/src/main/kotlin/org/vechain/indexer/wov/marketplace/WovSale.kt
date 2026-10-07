package org.vechain.indexer.wov.marketplace

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

/** How a marketplace sale was struck; mirrors the `wov_marketplace.mechanism` enum. */
enum class WovMechanism {
    CUSTODIAL,
    NON_CUSTODIAL,
    OFFER,
    AUCTION,
}

/** One completed marketplace purchase. [paymentToken] is the zero address for VET. */
data class WovSale(
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val contractAddress: String,
    val mechanism: WovMechanism,
    val marketId: BigInteger,
    val nft: String,
    val tokenId: BigInteger,
    val buyer: String,
    val paymentToken: String,
    val price: BigInteger,
) : IndexedDocument

/** The token and price a custodial listing or an auction announced, keyed by its market id. */
data class WovSaleTerms(
    val contractAddress: String,
    val marketId: BigInteger,
    val blockNumber: Long,
    val paymentToken: String,
    val price: BigInteger,
) {
    val key: WovTermsKey
        get() = WovTermsKey(contractAddress, marketId)
}

data class WovTermsKey(val contractAddress: String, val marketId: BigInteger)

/** A buyer's cumulative items and spend in one token after [blockNumber]. */
data class WovBuyerRunning(
    val buyer: String,
    val paymentToken: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val items: Long,
    val spend: BigInteger,
)

/** A buyer's first purchase, which is where the address-ordered page can skip them from. */
data class WovBuyer(val address: String, val firstBlockNumber: Long, val firstBlockTimestamp: Long)

/** Everything one entry writes: its sales, the terms it saw, the totals they moved, its end. */
data class WovMarketplaceEntry(
    val endBlockNumber: Long,
    val endBlockTimestamp: Long,
    val sales: List<WovSale> = emptyList(),
    val terms: List<WovSaleTerms> = emptyList(),
    val running: List<WovBuyerRunning> = emptyList(),
    val buyers: List<WovBuyer> = emptyList(),
)

/** One buyer's window totals: a running row at `to` minus the one at `from`, per token. */
data class WovBuyerWindow(val buyer: String, val spend: List<WovTokenSpend>) {
    val items: Long
        get() = spend.sumOf { it.items }
}

data class WovTokenSpend(val paymentToken: String, val items: Long, val spend: BigInteger)
