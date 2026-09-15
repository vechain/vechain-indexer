package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction

@ExtendWith(MockKExtension::class)
internal class SafeServiceTest {

    @MockK lateinit var repository: SafeReadRepository

    private val safe = "0x1111111111111111111111111111111111111111"
    private val owner = "0xAAAA111111111111111111111111111111111111"
    private val txHash = "0x" + "a".repeat(64)

    private lateinit var service: SafeService

    @BeforeEach
    fun setUp() {
        service = SafeService(repository)
    }

    private fun pageable(field: String) = PageRequest.of(0, 20, Sort.by(Direction.DESC, field))

    private fun membership() =
        SafeMembership(
            id = SafeMembership.buildId(safe, owner),
            safe = safe,
            owner = owner.lowercase(),
            addedBlock = 5L,
            addedTimestamp = 500L,
            blockId = "0xblock",
            blockNumber = 5L,
            blockTimestamp = 500L,
        )

    @Test
    fun `an owner's Safes are read with the scope and the normalised address`() {
        every { repository.findMembershipsByOwner(any(), any(), any(), any(), any()) } returns
            listOf(membership())

        val page = service.getSafesForOwner(owner, SafeMembershipScope.PAST, pageable("addedBlock"))

        assertEquals(1, page.content.size)
        verify {
            repository.findMembershipsByOwner(
                owner.lowercase(),
                SafeMembershipScope.PAST,
                0,
                21,
                Direction.DESC,
            )
        }
    }

    @Test
    fun `a Safe's proposals are paged newest first`() {
        every { repository.findProposalsBySafe(any(), any(), any(), any()) } returns emptyList()

        service.listProposals(safe.uppercase(), pageable("blockNumber"))

        verify { repository.findProposalsBySafe(safe, 0, 21, Direction.DESC) }
    }

    @Test
    fun `an unseen transaction reads back as an empty state, not as nothing`() {
        every { repository.findTxState(any(), any()) } returns null

        val state = service.getTxState(safe, txHash)

        assertEquals(SafeTxState.buildId(safe, txHash), state.id)
        assertEquals(emptyList<SafeTxApproval>(), state.approvers)
        assertFalse(state.executed)
        assertEquals(0L, state.blockNumber)
    }

    @Test
    fun `a known transaction reads back with its approvals`() {
        val state =
            SafeTxState(
                id = SafeTxState.buildId(safe, txHash),
                safe = safe,
                txHash = txHash,
                approvers = listOf(SafeTxApproval(owner.lowercase(), 5L, 500L, "0xtx")),
                executed = true,
                blockId = "0xblock",
                blockNumber = 6L,
                blockTimestamp = 600L,
            )
        every { repository.findTxState(safe, txHash) } returns state

        val found = service.getTxState(safe, txHash)

        assertTrue(found.executed)
        assertEquals(1, found.approvers.size)
    }

    @Test
    fun `the indexed head comes from the proxies`() {
        every { repository.latestBlockNumber() } returns 42L

        assertEquals(mapOf("Safe" to 42L), service.getLatestIndexedBlocks())
    }
}
