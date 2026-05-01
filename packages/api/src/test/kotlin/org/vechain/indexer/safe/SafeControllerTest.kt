package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.vechain.indexer.thor.Address

@ExtendWith(MockKExtension::class)
internal class SafeControllerTest {

    @MockK lateinit var safeService: SafeService

    private val safe = "0x1111111111111111111111111111111111111111"
    private val owner = "0xAAAA111111111111111111111111111111111111"
    private val txHash = "0x" + "a".repeat(64)

    private lateinit var controller: SafeController

    @BeforeEach
    fun setUp() {
        controller = SafeController(safeService)
    }

    @Test
    fun `getSafesForOwner uses ALL scope by default and orders by addedBlock desc`() {
        val pageableSlot = slot<Pageable>()
        every { safeService.getSafesForOwner(any(), any(), capture(pageableSlot)) } returns
            SliceImpl(emptyList<SafeMembership>())

        controller.getSafesForOwner(Address(owner), SafeMembershipScope.ALL, null, null, null)

        verify(exactly = 1) { safeService.getSafesForOwner(owner, SafeMembershipScope.ALL, any()) }
        assertEquals(
            Sort.Direction.DESC,
            pageableSlot.captured.sort.getOrderFor("addedBlock")?.direction,
        )
    }

    @Test
    fun `getTransactionsForSafe orders by blockNumber desc by default`() {
        val pageableSlot = slot<Pageable>()
        every { safeService.listProposals(any(), capture(pageableSlot)) } returns
            SliceImpl(emptyList<SafeTxProposal>())

        controller.getTransactionsForSafe(Address(safe), null, null, null)

        verify(exactly = 1) { safeService.listProposals(safe, any()) }
        assertEquals(
            Sort.Direction.DESC,
            pageableSlot.captured.sort.getOrderFor("blockNumber")?.direction,
        )
    }

    @Test
    fun `getTransactionsForSafe forwards pagination params`() {
        val pageableSlot = slot<Pageable>()
        every { safeService.listProposals(any(), capture(pageableSlot)) } returns
            SliceImpl(emptyList<SafeTxProposal>())

        controller.getTransactionsForSafe(Address(safe), 2, 50, "ASC")

        val captured = pageableSlot.captured
        assertEquals(2, captured.pageNumber)
        assertEquals(50, captured.pageSize)
        assertEquals(Sort.Direction.ASC, captured.sort.getOrderFor("blockNumber")?.direction)
    }

    @Test
    fun `getTxState delegates to the service`() {
        val doc =
            SafeTxState(
                id = SafeTxState.buildId(safe, txHash),
                safe = safe.lowercase(),
                txHash = txHash.lowercase(),
                blockId = "",
                blockNumber = 0L,
                blockTimestamp = 0L,
                version = 0,
            )
        every { safeService.getTxState(safe, txHash) } returns doc

        val result = controller.getTxState(Address(safe), txHash)

        assertEquals(doc, result)
    }
}
