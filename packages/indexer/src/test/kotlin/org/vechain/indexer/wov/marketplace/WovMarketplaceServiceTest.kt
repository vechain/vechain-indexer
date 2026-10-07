package org.vechain.indexer.wov.marketplace

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent

class WovMarketplaceServiceTest {
    private val repository = mockk<WovMarketplaceWriteRepository>(relaxed = true)
    private val service = WovMarketplaceService(repository)

    private val custodial = "0x" + "c1".repeat(20)
    private val nonCustodial = "0x" + "c2".repeat(20)
    private val offers = "0x" + "c3".repeat(20)
    private val auctions = "0x" + "c4".repeat(20)
    private val nft = "0x" + "4e".repeat(20)
    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val seller = "0x" + "5e".repeat(20)
    private val vet = "0x" + "00".repeat(20)
    private val wov = "0x" + "11".repeat(20)
    private val vvet = "0x" + "22".repeat(20)

    @BeforeEach
    fun setup() {
        every { repository.findTerms(any()) } returns emptyList()
        every { repository.findRunningBefore(any(), any()) } returns emptyList()
    }

    @Test
    fun `a non-custodial purchase is a sale on its own, in VET unless it says VIP180`() {
        val entry =
            service.process(
                listOf(
                    purchaseNonCustodial(1, alice, 100, isVip180 = false, token = wov, block = 10),
                    purchaseNonCustodial(2, alice, 7, isVip180 = true, token = wov, block = 10),
                ),
                10,
                100,
            )

        assertEquals(listOf(vet, wov), entry.sales.map { it.paymentToken })
        assertEquals(WovMechanism.NON_CUSTODIAL, entry.sales.first().mechanism)
        assertEquals(nft, entry.sales.first().nft)
        assertEquals(BigInteger.ONE, entry.sales.first().marketId)
        assertEquals(
            listOf(running(alice, vet, 10, 1, 100), running(alice, wov, 10, 1, 7)),
            entry.running,
        )
        assertEquals(listOf(WovBuyer(alice, 10, 100)), entry.buyers)
        assertEquals(emptyList<WovSaleTerms>(), entry.terms)
        verify(exactly = 0) { repository.findTerms(match { it.isNotEmpty() }) }
    }

    @Test
    fun `an accepted offer's buyer is the offerer in the event, not the seller who sent the tx`() {
        val entry =
            service.process(
                listOf(
                    offerAccepted(5, alice, 50, vvet, origin = seller),
                    offerAccepted(6, bob, 9, vet, origin = seller),
                ),
                10,
                100,
            )

        assertEquals(listOf(alice, bob), entry.sales.map { it.buyer })
        assertEquals(listOf(vvet, vet), entry.sales.map { it.paymentToken })
        assertEquals(WovMechanism.OFFER, entry.sales.first().mechanism)
        assertEquals(BigInteger.valueOf(50), entry.sales.first().price)
    }

    @Test
    fun `a custodial purchase takes its token and price from the listing, in-entry or stored`() {
        val sameEntry =
            service.process(
                listOf(
                    listing(1, 300, isVip180 = true, token = wov, block = 10),
                    purchase(1, alice, 12),
                ),
                12,
                120,
            )
        assertEquals(
            listOf(WovSaleTerms(custodial, BigInteger.ONE, 10, wov, BigInteger.valueOf(300))),
            sameEntry.terms,
        )
        assertEquals(wov, sameEntry.sales.single().paymentToken)
        assertEquals(BigInteger.valueOf(300), sameEntry.sales.single().price)
        assertEquals(WovMechanism.CUSTODIAL, sameEntry.sales.single().mechanism)
        verify(exactly = 0) { repository.findTerms(match { it.isNotEmpty() }) }

        every { repository.findTerms(setOf(WovTermsKey(custodial, BigInteger.TWO))) } returns
            listOf(WovSaleTerms(custodial, BigInteger.TWO, 5, vet, BigInteger.valueOf(80)))
        val laterEntry = service.process(listOf(purchase(2, bob, 20)), 20, 200)
        assertEquals(vet, laterEntry.sales.single().paymentToken)
        assertEquals(BigInteger.valueOf(80), laterEntry.sales.single().price)
        assertEquals(emptyList<WovSaleTerms>(), laterEntry.terms)
    }

    @Test
    fun `a settled auction prices at the winning bid in the token the auction opened with`() {
        every { repository.findTerms(setOf(WovTermsKey(auctions, BigInteger.valueOf(8)))) } returns
            listOf(WovSaleTerms(auctions, BigInteger.valueOf(8), 5, vvet, BigInteger.ZERO))

        val entry =
            service.process(
                listOf(
                    newAuction(9, isVip180 = false, token = wov, block = 30),
                    auctionExecuted(8, alice, 400, block = 31),
                    auctionExecuted(9, bob, 25, block = 32),
                ),
                32,
                320,
            )

        assertEquals(listOf(vvet, vet), entry.sales.map { it.paymentToken })
        assertEquals(listOf(400L, 25L), entry.sales.map { it.price.toLong() })
        assertEquals(listOf(alice, bob), entry.sales.map { it.buyer })
        assertEquals(WovMechanism.AUCTION, entry.sales.first().mechanism)
        assertEquals(BigInteger.ZERO, entry.terms.single().price)
    }

    @Test
    fun `an auction executed at a price of zero went unsold and is no sale`() {
        val entry = service.process(listOf(auctionExecuted(8, seller, 0, block = 31)), 31, 310)

        assertEquals(emptyList<WovSale>(), entry.sales)
        verify(exactly = 0) { repository.findTerms(match { it.isNotEmpty() }) }
    }

    @Test
    fun `a settlement whose terms were never announced is an error, not a skipped sale`() {
        assertThrows(IllegalStateException::class.java) {
            service.process(listOf(purchase(99, alice, 12)), 12, 120)
        }
        assertThrows(IllegalStateException::class.java) {
            service.process(listOf(auctionExecuted(99, alice, 1, block = 12)), 12, 120)
        }
    }

    @Test
    fun `a cart checkout of 31 purchases in one tx is 31 items in one running row`() {
        val cart =
            (1..31).map {
                purchaseNonCustodial(it.toLong(), alice, 10, false, wov, 40, logIndex = it.toLong())
            }

        val entry = service.process(cart, 40, 400)

        assertEquals(31, entry.sales.size)
        assertEquals(listOf(running(alice, vet, 40, 31, 310)), entry.running)
        assertEquals(listOf(WovBuyer(alice, 40, 400)), entry.buyers)
    }

    @Test
    fun `running totals continue the stored row and take one row per block, in block order`() {
        every { repository.findRunningBefore(listOf(alice to vet), 50) } returns
            listOf(running(alice, vet, 20, 3, 300))

        val entry =
            service.process(
                listOf(
                    purchaseNonCustodial(3, alice, 5, false, wov, block = 52),
                    purchaseNonCustodial(1, alice, 10, false, wov, block = 50, logIndex = 1),
                    purchaseNonCustodial(2, alice, 20, false, wov, block = 50, logIndex = 0),
                ),
                60,
                600,
            )

        assertEquals(listOf(50L, 50L, 52L), entry.sales.map { it.blockNumber })
        assertEquals(listOf(2L, 1L, 3L), entry.sales.map { it.marketId.toLong() })
        assertEquals(
            listOf(running(alice, vet, 50, 5, 330), running(alice, vet, 52, 6, 335)),
            entry.running,
        )
        assertEquals(listOf(WovBuyer(alice, 50, 500)), entry.buyers)
        assertEquals(60L, entry.endBlockNumber)
    }

    @Test
    fun `the same entry processed twice is the same entry, seeded before its first block`() {
        every { repository.findRunningBefore(listOf(bob to vet), 70) } returns
            listOf(running(bob, vet, 60, 1, 1))
        val events = listOf(purchaseNonCustodial(1, bob, 2, false, wov, block = 70))

        assertEquals(service.process(events, 70, 700), service.process(events, 70, 700))
        verify(exactly = 2) { repository.findRunningBefore(listOf(bob to vet), 70) }
        assertEquals(listOf(running(bob, vet, 70, 2, 3)), service.process(events, 70, 700).running)
    }

    @Test
    fun `an entry without events still carries its end block for the progress marker`() {
        assertEquals(WovMarketplaceEntry(80, 800), service.process(emptyList(), 80, 800))
        verify(exactly = 0) { repository.findRunningBefore(any(), any()) }
    }

    private fun running(buyer: String, token: String, block: Long, items: Long, spend: Long) =
        WovBuyerRunning(buyer, token, block, block * 10, items, BigInteger.valueOf(spend))

    private fun listing(saleId: Long, price: Long, isVip180: Boolean, token: String, block: Long) =
        event(
            custodial,
            "listing",
            block,
            mapOf(
                "saleId" to BigInteger.valueOf(saleId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(saleId),
                "seller" to seller,
                "price" to BigInteger.valueOf(price),
                "startingTime" to BigInteger.ZERO,
                "isVIP180" to isVip180,
                "addressVIP180" to token,
            ),
        )

    private fun purchase(saleId: Long, buyer: String, block: Long) =
        event(
            custodial,
            "purchase",
            block,
            mapOf(
                "saleId" to BigInteger.valueOf(saleId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(saleId),
                "buyer" to buyer,
            ),
        )

    private fun purchaseNonCustodial(
        saleId: Long,
        buyer: String,
        price: Long,
        isVip180: Boolean,
        token: String,
        block: Long,
        logIndex: Long = saleId,
    ) =
        event(
            nonCustodial,
            "purchaseNonCustodial",
            block,
            mapOf(
                "saleId" to BigInteger.valueOf(saleId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(saleId),
                "buyer" to buyer,
                "price" to BigInteger.valueOf(price),
                "startingTime" to BigInteger.ZERO,
                "isVIP180" to isVip180,
                "addressVIP180" to token,
            ),
            logIndex = logIndex,
        )

    private fun offerAccepted(
        offerId: Long,
        buyer: String,
        value: Long,
        token: String,
        origin: String,
    ) =
        event(
            offers,
            "OfferAccepted",
            10,
            mapOf(
                "offerId" to BigInteger.valueOf(offerId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(offerId),
                "offerType" to BigInteger.ONE,
                "buyer" to buyer,
                "value" to BigInteger.valueOf(value),
                "vip180" to token,
            ),
            origin = origin,
            logIndex = offerId,
        )

    private fun newAuction(auctionId: Long, isVip180: Boolean, token: String, block: Long) =
        event(
            auctions,
            "newAuction",
            block,
            mapOf(
                "auctionId" to BigInteger.valueOf(auctionId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(auctionId),
                "seller" to seller,
                "price" to BigInteger.TEN,
                "startingTime" to BigInteger.ZERO,
                "endTime" to BigInteger.ZERO,
                "isVIP180" to isVip180,
                "addressVIP180" to token,
            ),
        )

    private fun auctionExecuted(auctionId: Long, newOwner: String, price: Long, block: Long) =
        event(
            auctions,
            "auctionExecuted",
            block,
            mapOf(
                "auctionId" to BigInteger.valueOf(auctionId),
                "nft" to nft,
                "tokenId" to BigInteger.valueOf(auctionId),
                "newOwner" to newOwner,
                "price" to BigInteger.valueOf(price),
            ),
        )

    private fun event(
        contract: String,
        type: String,
        block: Long,
        params: Map<String, Any>,
        origin: String = alice,
        logIndex: Long = 0,
    ): IndexedEvent =
        IndexedEvent(
            id = "$type-$block-$contract-$logIndex-${params.values.first()}",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0x" + "ff".repeat(32),
            origin = origin,
            params = AbiEventParameters(params, type),
            address = contract.uppercase().replace("0X", "0x"),
            eventType = type,
            clauseIndex = 0,
            txIndex = 0,
            logIndex = logIndex,
        )
}
