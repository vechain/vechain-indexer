package org.vechain.indexer.stargate.marketplace

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Turns one entry's marketplace events into the listing states they leave behind. */
@Profile("stargate-marketplace")
@Service
open class StargateMarketplaceService(
    private val repository: StargateMarketplaceWriteRepository,
    @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}") stargateNft: String,
) {
    private val stargateNft = HexUtils.normalise(stargateNft)

    /**
     * One row per state change on the Stargate collection, in log order; a purchase takes its
     * seller from the listing it settles, in this entry or already stored.
     */
    open fun process(events: List<IndexedEvent>): List<StargateListing> {
        val ordered = events.filter { address(it, "nft") == stargateNft }.sortedWith(ORDER)
        val listedHere = ordered.filter { it.type() == LISTING }.map { saleId(it) }.toSet()
        val settled = ordered.filter { it.type() == PURCHASE }.map { saleId(it) } - listedHere
        val current = repository.findCurrent(settled).associateBy { it.saleId }.toMutableMap()
        return ordered.mapNotNull { event ->
            val row =
                when (event.type()) {
                    LISTING -> listing(event, ListingStatus.OPEN, address(event, "seller"))
                    PURCHASE -> {
                        val open =
                            checkNotNull(current[saleId(event)]) {
                                "$PURCHASE in tx ${event.txId} settles sale ${saleId(event)}, " +
                                    "which was never listed"
                            }
                        listing(event, ListingStatus.SOLD, open.seller, address(event, "buyer"))
                    }
                    CANCEL -> listing(event, ListingStatus.CANCELLED, address(event, "seller"))
                    else -> null
                }
            row?.also { current[it.saleId] = it }
        }
    }

    open fun save(listings: List<StargateListing>) = repository.save(listings)

    private fun listing(
        event: IndexedEvent,
        status: ListingStatus,
        seller: String,
        buyer: String? = null,
    ): StargateListing =
        StargateListing(
            saleId = saleId(event),
            blockNumber = event.blockNumber,
            blockId = event.blockId,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            tokenId = quantity(event, "tokenId"),
            seller = seller,
            price = quantity(event, "price"),
            paymentToken = paymentToken(event),
            status = status,
            buyer = buyer,
        )

    /** `isVIP180 = false` is native VET whatever `addressVIP180` holds. */
    private fun paymentToken(event: IndexedEvent): String =
        if (event.params.getAsBoolean("isVIP180") == true) address(event, "addressVIP180")
        else Address.ZERO_ADDRESS

    private fun IndexedEvent.type(): String? = params.getEventType()

    private fun saleId(event: IndexedEvent): BigInteger = quantity(event, "saleId")

    private fun address(event: IndexedEvent, field: String): String =
        HexUtils.normalise(
            event.params.getAsString(field) ?: error("Missing '$field' in event ${event.id}")
        )

    private fun quantity(event: IndexedEvent, field: String): BigInteger =
        event.params.getAsBigInteger(field) ?: error("Missing '$field' in event ${event.id}")

    companion object {
        const val LISTING = "ListingNonCustodial"
        const val PURCHASE = "PurchaseNonCustodial"
        const val CANCEL = "CancelNonCustodial"

        val EVENTS = listOf(LISTING, PURCHASE, CANCEL)

        private val ORDER =
            compareBy<IndexedEvent>(
                { it.blockNumber },
                { it.txIndex ?: 0 },
                { it.clauseIndex },
                { it.logIndex ?: 0 },
            )
    }
}
