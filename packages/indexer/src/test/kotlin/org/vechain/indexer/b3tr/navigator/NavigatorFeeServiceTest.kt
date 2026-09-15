package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.b3tr.navigator.NavigatorFeeService.Companion.FEE_CLAIMED
import org.vechain.indexer.b3tr.navigator.NavigatorFeeService.Companion.FEE_DEPOSITED
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class NavigatorFeeServiceTest {
    @MockK lateinit var repository: NavigatorWriteRepository

    private lateinit var service: NavigatorFeeService

    private val block = BlockDetails("0xblock", 100L, 1000L)
    private val nav = "0xaaaa111111111111111111111111111111111111"

    @BeforeEach
    fun setUp() {
        every { repository.findCurrentFees(any(), any()) } returns emptyList()
        service = NavigatorFeeService(repository)
    }

    private fun event(type: String, round: String, amount: String): IndexedEvent =
        buildIndexedEvent(
            eventType = type,
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
            params =
                AbiEventParameters(
                    mapOf("navigator" to nav.uppercase(), "roundId" to round, "amount" to amount),
                    type,
                ),
        )

    private fun stored(deposited: String, claimed: String? = null) =
        NavigatorFee(
            navigator = nav,
            roundId = 5,
            blockId = "0xold",
            blockNumber = 50L,
            blockTimestamp = 500L,
            totalDeposited = BigDecimal(deposited),
            claimedAmount = claimed?.let(::BigDecimal),
            claimedAt = claimed?.let { 500L },
            depositedAt = 500L,
            unlockRound = 9L,
        )

    @Test
    fun `a first deposit opens the round's row, a second one in the block adds to it`() {
        val rows =
            service.processBlock(
                block,
                listOf(event(FEE_DEPOSITED, "5", "1000"), event(FEE_DEPOSITED, "5", "500")),
            )

        val row = rows.single()
        assertEquals(nav, row.navigator)
        assertEquals(5, row.roundId)
        assertEquals(BigDecimal("1500"), row.totalDeposited)
        assertEquals(9L, row.unlockRound)
        assertEquals(1000L, row.depositedAt)
        assertFalse(row.claimed)
        assertNull(row.claimedAt)
    }

    @Test
    fun `a deposit builds on the stored row and keeps when it was first deposited`() {
        every { repository.findCurrentFees(setOf(nav to 5), any()) } returns listOf(stored("1000"))

        val row = service.processBlock(block, listOf(event(FEE_DEPOSITED, "5", "250"))).single()

        assertEquals(BigDecimal("1250"), row.totalDeposited)
        assertEquals(500L, row.depositedAt)
        assertEquals(100L, row.blockNumber)
    }

    @Test
    fun `a claim records what was paid and when`() {
        every { repository.findCurrentFees(setOf(nav to 5), any()) } returns listOf(stored("1000"))

        val row = service.processBlock(block, listOf(event(FEE_CLAIMED, "5", "1000"))).single()

        assertTrue(row.claimed)
        assertEquals(BigDecimal("1000"), row.claimedAmount)
        assertEquals(1000L, row.claimedAt)
        assertEquals(BigDecimal("1000"), row.totalDeposited)
    }

    @Test
    fun `a claim for a round with no deposit is an error`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                service.processBlock(block, listOf(event(FEE_CLAIMED, "5", "1000")))
            }
        assertTrue(error.message!!.contains("roundId=5"))
    }

    @Test
    fun `other events are ignored`() {
        assertTrue(
            service.processBlock(block, listOf(event("B3TR_StakeAdded", "5", "1"))).isEmpty()
        )
    }
}
