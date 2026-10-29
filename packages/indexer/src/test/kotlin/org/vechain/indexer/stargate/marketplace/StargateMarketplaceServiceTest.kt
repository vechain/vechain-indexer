package org.vechain.indexer.stargate.marketplace

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address

class StargateMarketplaceServiceTest {
    private val repository = mockk<StargateMarketplaceWriteRepository>(relaxed = true)
    private val service =
        StargateMarketplaceService(repository, "0x1856C533ac2d94340AAA8544D35A5C1d4A21DEE7")

    private val stargate = "0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7"
    private val otherNft = "0x" + "4e".repeat(20)
    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val carol = "0x" + "cc".repeat(20)
    private val wov = "0x" + "11".repeat(20)

    @Test
    fun `a listing opens and its purchase closes it sold, with the listing's seller`() {
        val rows =
            service.process(listOf(listing(1, 7, alice, 100, block = 10), purchase(1, bob, 12)))

        assertEquals(listOf(ListingStatus.OPEN, ListingStatus.SOLD), rows.map { it.status })
        assertEquals(row(1, 7, alice, 100, 10), rows.first())
        assertEquals(row(1, 7, alice, 100, 12, ListingStatus.SOLD, buyer = bob), rows.last())
        verify(exactly = 0) { repository.findCurrent(match { it.isNotEmpty() }) }
    }

    @Test
    fun `a cancel closes the listing cancelled, and a VIP180 listing keeps its token`() {
        val rows =
            service.process(
                listOf(
                    listing(2, 8, alice, 50, block = 10, token = wov),
                    cancel(2, 8, alice, 50, block = 11, token = wov),
                )
            )

        assertEquals(
            listOf(
                row(2, 8, alice, 50, 10, token = wov),
                row(2, 8, alice, 50, 11, ListingStatus.CANCELLED, token = wov),
            ),
            rows,
        )
    }

    @Test
    fun `a re-listing in one tx cancels the old sale before it opens the new one, by log order`() {
        val rows =
            service.process(
                listOf(
                    listing(4, 9, alice, 120, block = 20, logIndex = 1),
                    cancel(3, 9, alice, 100, block = 20, logIndex = 0),
                )
            )

        assertEquals(
            listOf(
                row(3, 9, alice, 100, 20, ListingStatus.CANCELLED),
                row(4, 9, alice, 120, 20),
            ),
            rows,
        )
    }

    @Test
    fun `a purchase of a listing from an earlier entry takes its seller from the store`() {
        every {
            repository.findCurrent(match { it.toList() == listOf(BigInteger.valueOf(5)) })
        } returns listOf(row(5, 3, carol, 70, 25))

        val rows = service.process(listOf(purchase(5, bob, 30, tokenId = 3, price = 70)))

        assertEquals(listOf(row(5, 3, carol, 70, 30, ListingStatus.SOLD, buyer = bob)), rows)
    }

    @Test
    fun `a purchase of a sale that was never listed is an error, not a skipped row`() {
        assertThrows(IllegalStateException::class.java) {
            service.process(listOf(purchase(99, bob, 30)))
        }
    }

    @Test
    fun `events on another collection are ignored`() {
        val rows =
            service.process(
                listOf(
                    listing(6, 1, alice, 10, block = 10, nft = otherNft),
                    purchase(6, bob, 11, nft = otherNft),
                )
            )

        assertEquals(emptyList<StargateListing>(), rows)
        verify(exactly = 0) { repository.findCurrent(match { it.isNotEmpty() }) }
    }

    private fun row(
        saleId: Long,
        tokenId: Long,
        seller: String,
        price: Long,
        block: Long,
        status: ListingStatus = ListingStatus.OPEN,
        buyer: String? = null,
        token: String = Address.ZERO_ADDRESS,
    ) =
        StargateListing(
            saleId = BigInteger.valueOf(saleId),
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
            txId = "0x" + "ff".repeat(32),
            tokenId = BigInteger.valueOf(tokenId),
            seller = seller,
            price = BigInteger.valueOf(price),
            paymentToken = token,
            status = status,
            buyer = buyer,
        )

    private fun listing(
        saleId: Long,
        tokenId: Long,
        seller: String,
        price: Long,
        block: Long,
        token: String? = null,
        nft: String = stargate,
        logIndex: Long = 0,
    ) =
        event(
            StargateMarketplaceService.LISTING,
            block,
            params(saleId, nft, tokenId, "seller" to seller, price, token),
            logIndex,
        )

    private fun cancel(
        saleId: Long,
        tokenId: Long,
        seller: String,
        price: Long,
        block: Long,
        token: String? = null,
        logIndex: Long = 0,
    ) =
        event(
            StargateMarketplaceService.CANCEL,
            block,
            params(saleId, stargate, tokenId, "seller" to seller, price, token),
            logIndex,
        )

    private fun purchase(
        saleId: Long,
        buyer: String,
        block: Long,
        tokenId: Long = 7,
        price: Long = 100,
        nft: String = stargate,
    ) =
        event(
            StargateMarketplaceService.PURCHASE,
            block,
            params(saleId, nft, tokenId, "buyer" to buyer, price, null),
        )

    private fun params(
        saleId: Long,
        nft: String,
        tokenId: Long,
        party: Pair<String, String>,
        price: Long,
        token: String?,
    ) =
        mapOf(
            "saleId" to BigInteger.valueOf(saleId),
            "nft" to nft,
            "tokenId" to BigInteger.valueOf(tokenId),
            party,
            "price" to BigInteger.valueOf(price),
            "code" to "0x766574",
            "startingTime" to BigInteger.ZERO,
            "isVIP180" to (token != null),
            "addressVIP180" to (token ?: Address.ZERO_ADDRESS),
        )

    private fun event(
        type: String,
        block: Long,
        params: Map<String, Any>,
        logIndex: Long = 0,
    ): IndexedEvent =
        IndexedEvent(
            id = "$type-$block-$logIndex-${params["saleId"]}",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0x" + "ff".repeat(32),
            origin = alice,
            params = AbiEventParameters(params, type),
            address = "0x0602cfd406a3f592eb19246c535d855fb3ddaaf4",
            eventType = type,
            clauseIndex = 0,
            txIndex = 0,
            logIndex = logIndex,
        )
}
