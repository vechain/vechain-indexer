package org.vechain.indexer.stargate.marketplace

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils

internal class StargateMarketplaceServiceTest {
    private val repository = mockk<StargateMarketplaceReadRepository>()
    private val service = StargateMarketplaceService(repository)

    private val alice = "0x" + "aa".repeat(20)
    private val carol = "0x" + "cc".repeat(20)

    @Test
    fun `a token without an open listing is not found, whichever way its id is written`() {
        every { repository.findOpenByToken("255") } returns null

        assertThrows(ResourceNotFoundException::class.java) { service.listing("0xff") }
        assertThrows(ResourceNotFoundException::class.java) { service.listing("255") }
    }

    @Test
    fun `the flags say whether the token still exists and who holds it`() {
        every { repository.findOpenByToken("1") } returns ListedToken(row(1), null, null, null)
        val unknown = service.listing("1")
        assertFalse(unknown.tokenExists)
        assertFalse(unknown.sellerIsOwner)
        assertNull(unknown.level)
        assertNull(unknown.vetStaked)

        every { repository.findOpenByToken("2") } returns
            ListedToken(row(2), TokenLevel.Dawn, BigInteger.TEN, Address.ZERO_ADDRESS)
        val burned = service.listing("2")
        assertFalse(burned.tokenExists)
        assertFalse(burned.sellerIsOwner)
        assertEquals(TokenLevel.Dawn, burned.level)

        every { repository.findOpenByToken("3") } returns
            ListedToken(row(3), TokenLevel.Dawn, BigInteger.TEN, carol)
        val transferred = service.listing("3")
        assertTrue(transferred.tokenExists)
        assertFalse(transferred.sellerIsOwner)

        every { repository.findOpenByToken("4") } returns
            ListedToken(row(4), TokenLevel.Flash, BigInteger.TEN, alice)
        val held = service.listing("4")
        assertTrue(held.tokenExists)
        assertTrue(held.sellerIsOwner)
        assertEquals("4", held.saleId)
        assertEquals("4", held.tokenId)
        assertEquals(MarketplaceListing.ListedAt(10, 100), held.listedAt)
    }

    @Test
    fun `the page passes its filters and order through and reads one row past itself`() {
        every {
            repository.findValid(TokenLevel.Flash, alice, ListingSort.PRICE, Direction.ASC, 2, 3)
        } returns
            listOf(
                ListedToken(row(1), TokenLevel.Flash, BigInteger.TEN, alice),
                ListedToken(row(2), TokenLevel.Flash, BigInteger.TEN, alice),
                ListedToken(row(3), TokenLevel.Flash, BigInteger.TEN, alice),
            )

        val page =
            service.listings(
                "flash",
                alice,
                ListingSort.PRICE,
                PaginationUtils.toPageable(1, 2, "asc", ListingSort.PRICE.name),
            )

        assertEquals(listOf("1", "2"), page.data.map { it.saleId })
        assertTrue(page.pagination.hasNext)
        assertTrue(page.data.all { it.tokenExists && it.sellerIsOwner })
    }

    private fun row(id: Long) =
        StargateListing(
            saleId = BigInteger.valueOf(id),
            blockNumber = 10,
            blockId = "0x" + "0".repeat(63) + "a",
            blockTimestamp = 100,
            txId = "0x" + "ff".repeat(32),
            tokenId = BigInteger.valueOf(id),
            seller = alice,
            price = BigInteger.valueOf(90),
            paymentToken = Address.ZERO_ADDRESS,
            status = ListingStatus.OPEN,
        )
}
