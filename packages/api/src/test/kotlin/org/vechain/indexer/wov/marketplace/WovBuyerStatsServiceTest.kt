package org.vechain.indexer.wov.marketplace

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.WindowNotIndexedException

internal class WovBuyerStatsServiceTest {
    private val repository = mockk<WovMarketplaceReadRepository>()
    private val service = WovBuyerStatsService(repository)

    private val vet = "0x" + "00".repeat(20)
    private val wov = "0x170f4ba8e7acf6510f55db26047c83d13498af8a"
    private val vvet = "0x45429a2255e7248e57fce99e7239aed3f84b7a53"
    private val other = "0x" + "99".repeat(20)

    private fun buyer(n: Int) = "0x" + n.toString(16).padStart(40, '0')

    private fun window(n: Int, vararg spend: WovTokenSpend) =
        WovBuyerWindow(buyer(n), spend.toList())

    private fun spend(token: String, items: Long, amount: Long) =
        WovTokenSpend(token, items, BigInteger.valueOf(amount))

    init {
        every { repository.indexedThrough() } returns 500L
        every { repository.buyers(any(), any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `a window must start before it ends`() {
        assertThrows(BadRequestException::class.java) { service.buyers(300, 300, null, null) }
        assertThrows(BadRequestException::class.java) { service.buyers(301, 300, null, null) }
        verify(exactly = 0) { repository.buyers(any(), any(), any(), any()) }
    }

    @Test
    fun `a window ending past the indexed head is refused and says how far the index is`() {
        val refused =
            assertThrows(WindowNotIndexedException::class.java) {
                service.buyers(0, 501, null, null)
            }
        assertEquals(501L, refused.to)
        assertEquals(500L, refused.indexedThrough)
        assertEquals("to=501 is beyond indexedThrough=500", refused.message)

        every { repository.indexedThrough() } returns null
        val empty =
            assertThrows(WindowNotIndexedException::class.java) { service.buyers(0, 1, null, null) }
        assertNull(empty.indexedThrough)
        assertEquals("nothing is indexed yet", empty.message)
    }

    @Test
    fun `a window ending at the indexed head is answered, and the cursor carries its end`() {
        every { repository.buyers(0, 500, null, 3) } returns
            listOf(window(1, spend(vet, 1, 10)), window(2, spend(vet, 1, 10)), window(3))

        val page = service.buyers(0, 500, 2, null)

        assertEquals(listOf(buyer(1), buyer(2)), page.data.map { it.buyer })
        assertTrue(page.pagination.hasNext)
        assertEquals("500|${buyer(2)}", page.pagination.cursor)
    }

    @Test
    fun `a cursor resumes after its address, only for the window end it carries`() {
        every { repository.buyers(0, 400, buyer(2), 21) } returns
            listOf(window(3, spend(wov, 2, 5)))

        val page = service.buyers(0, 400, null, "400|${buyer(2).uppercase().replace("0X", "0x")}")

        assertEquals(listOf(buyer(3)), page.data.map { it.buyer })
        assertFalse(page.pagination.hasNext)
        assertNull(page.pagination.cursor)
        assertEquals(page, service.buyers(0, 400, null, "400|${buyer(2)}"))
        assertThrows(BadRequestException::class.java) {
            service.buyers(0, 401, null, "400|${buyer(2)}")
        }
        assertThrows(BadRequestException::class.java) { service.buyers(0, 400, null, "400|nobody") }
        assertThrows(BadRequestException::class.java) {
            service.buyers(0, 400, null, "4.5|${buyer(2)}")
        }
    }

    @Test
    fun `a buyer's row names each token, its symbol where known, and the items over all of them`() {
        every { repository.buyers(100, 200, null, 21) } returns
            listOf(
                window(
                    7,
                    spend(vet, 2, 3_000),
                    spend(wov, 1, 5),
                    spend(vvet, 1, 7),
                    spend(other, 1, 1),
                )
            )

        val row = service.buyers(100, 200, null, null).data.single()

        assertEquals(5L, row.itemCount)
        assertEquals(listOf("VET", "WoV", "VVET", null), row.spend.map { it.symbol })
        assertEquals(listOf(vet, wov, vvet, other), row.spend.map { it.token })
        assertEquals(listOf("3000", "5", "7", "1"), row.spend.map { it.amount })
        assertEquals(listOf(2L, 1L, 1L, 1L), row.spend.map { it.items })
    }
}
