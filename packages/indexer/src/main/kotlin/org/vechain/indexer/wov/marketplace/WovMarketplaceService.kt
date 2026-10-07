package org.vechain.indexer.wov.marketplace

import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Turns one entry's World of V events into sales, terms and the running totals they move. */
@Profile("wov-marketplace")
@Service
open class WovMarketplaceService(private val repository: WovMarketplaceWriteRepository) {

    open fun process(
        events: List<IndexedEvent>,
        endBlock: Long,
        endTimestamp: Long,
    ): WovMarketplaceEntry {
        val ordered = events.sortedWith(ORDER)
        val terms = ordered.mapNotNull(::termsOf)
        val announced = mutableMapOf<WovTermsKey, WovSaleTerms>()
        terms.forEach { announced.putIfAbsent(it.key, it) }
        val wanted = ordered.filter(::settles).map(::keyOf).filter { it !in announced }.toSet()
        repository.findTerms(wanted).forEach { announced.putIfAbsent(it.key, it) }
        val sales = ordered.mapNotNull { saleOf(it, announced) }
        return WovMarketplaceEntry(
            endBlockNumber = endBlock,
            endBlockTimestamp = endTimestamp,
            sales = sales,
            terms = terms,
            running = running(sales),
            buyers =
                sales
                    .groupBy { it.buyer }
                    .map { (buyer, bought) ->
                        WovBuyer(buyer, bought.first().blockNumber, bought.first().blockTimestamp)
                    },
        )
    }

    open fun save(entry: WovMarketplaceEntry) = repository.save(entry)

    private fun termsOf(event: IndexedEvent): WovSaleTerms? =
        when (event.params.getEventType()) {
            LISTING ->
                WovSaleTerms(
                    contract(event),
                    quantity(event, "saleId"),
                    event.blockNumber,
                    announcedToken(event),
                    quantity(event, "price"),
                )
            NEW_AUCTION ->
                WovSaleTerms(
                    contract(event),
                    quantity(event, "auctionId"),
                    event.blockNumber,
                    announcedToken(event),
                    BigInteger.ZERO,
                )
            else -> null
        }

    // An auction that found no bidder is "executed" back to its seller at a price of zero.
    private fun settles(event: IndexedEvent): Boolean =
        when (event.params.getEventType()) {
            PURCHASE -> true
            AUCTION_EXECUTED -> quantity(event, "price").signum() > 0
            else -> false
        }

    private fun keyOf(event: IndexedEvent): WovTermsKey =
        WovTermsKey(
            contract(event),
            quantity(event, if (event.params.getEventType() == PURCHASE) "saleId" else "auctionId"),
        )

    private fun saleOf(event: IndexedEvent, announced: Map<WovTermsKey, WovSaleTerms>): WovSale? =
        when (event.params.getEventType()) {
            PURCHASE -> {
                val terms = termsFor(event, announced)
                sale(
                    event,
                    WovMechanism.CUSTODIAL,
                    "saleId",
                    "buyer",
                    terms.paymentToken,
                    terms.price,
                )
            }
            AUCTION_EXECUTED ->
                if (!settles(event)) null
                else {
                    val terms = termsFor(event, announced)
                    sale(
                        event,
                        WovMechanism.AUCTION,
                        "auctionId",
                        "newOwner",
                        terms.paymentToken,
                        quantity(event, "price"),
                    )
                }
            PURCHASE_NON_CUSTODIAL ->
                sale(
                    event,
                    WovMechanism.NON_CUSTODIAL,
                    "saleId",
                    "buyer",
                    announcedToken(event),
                    quantity(event, "price"),
                )
            OFFER_ACCEPTED ->
                sale(
                    event,
                    WovMechanism.OFFER,
                    "offerId",
                    "buyer",
                    address(event, "vip180"),
                    quantity(event, "value"),
                )
            else -> null
        }

    private fun termsFor(
        event: IndexedEvent,
        announced: Map<WovTermsKey, WovSaleTerms>,
    ): WovSaleTerms =
        checkNotNull(announced[keyOf(event)]) {
            "${event.params.getEventType()} in tx ${event.txId} settles ${keyOf(event)}, " +
                "which was never announced"
        }

    private fun sale(
        event: IndexedEvent,
        mechanism: WovMechanism,
        marketIdField: String,
        buyerField: String,
        paymentToken: String,
        price: BigInteger,
    ): WovSale =
        WovSale(
            id = DigestUtils.sha1Hex(event.id),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            contractAddress = contract(event),
            mechanism = mechanism,
            marketId = quantity(event, marketIdField),
            nft = address(event, "nft"),
            tokenId = quantity(event, "tokenId"),
            buyer = address(event, buyerField),
            paymentToken = paymentToken,
            price = price,
        )

    /** One row per (buyer, token, block): the cumulative total after that block's purchases. */
    private fun running(sales: List<WovSale>): List<WovBuyerRunning> {
        if (sales.isEmpty()) return emptyList()
        val pairs = sales.map { it.buyer to it.paymentToken }.distinct()
        val totals =
            repository
                .findRunningBefore(pairs, sales.first().blockNumber)
                .associate { (it.buyer to it.paymentToken) to (it.items to it.spend) }
                .toMutableMap()
        val rows = linkedMapOf<Triple<String, String, Long>, WovBuyerRunning>()
        sales.forEach { sale ->
            val key = sale.buyer to sale.paymentToken
            val (items, spend) = totals[key] ?: (0L to BigInteger.ZERO)
            totals[key] = (items + 1) to (spend + sale.price)
            rows[Triple(sale.buyer, sale.paymentToken, sale.blockNumber)] =
                WovBuyerRunning(
                    sale.buyer,
                    sale.paymentToken,
                    sale.blockNumber,
                    sale.blockTimestamp,
                    items + 1,
                    spend + sale.price,
                )
        }
        return rows.values.toList()
    }

    /** `isVIP180 = false` is native VET whatever `addressVIP180` holds. */
    private fun announcedToken(event: IndexedEvent): String =
        if (event.params.getAsBoolean("isVIP180") == true) address(event, "addressVIP180")
        else Address.ZERO_ADDRESS

    private fun contract(event: IndexedEvent): String =
        HexUtils.normalise(event.address ?: error("No contract address in event ${event.id}"))

    private fun address(event: IndexedEvent, field: String): String =
        HexUtils.normalise(
            event.params.getAsString(field) ?: error("Missing '$field' in event ${event.id}")
        )

    private fun quantity(event: IndexedEvent, field: String): BigInteger =
        event.params.getAsBigInteger(field) ?: error("Missing '$field' in event ${event.id}")

    companion object {
        const val LISTING = "listing"
        const val PURCHASE = "purchase"
        const val PURCHASE_NON_CUSTODIAL = "purchaseNonCustodial"
        const val OFFER_ACCEPTED = "OfferAccepted"
        const val NEW_AUCTION = "newAuction"
        const val AUCTION_EXECUTED = "auctionExecuted"

        val EVENTS =
            listOf(
                LISTING,
                PURCHASE,
                PURCHASE_NON_CUSTODIAL,
                OFFER_ACCEPTED,
                NEW_AUCTION,
                AUCTION_EXECUTED,
            )

        private val ORDER =
            compareBy<IndexedEvent>(
                { it.blockNumber },
                { it.txIndex ?: 0 },
                { it.clauseIndex },
                { it.logIndex ?: 0 },
            )
    }
}
