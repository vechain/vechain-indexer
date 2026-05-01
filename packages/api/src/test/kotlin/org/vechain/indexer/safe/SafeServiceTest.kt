package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.vechain.indexer.safe.repository.SafeMembershipRepository
import org.vechain.indexer.safe.repository.SafeTxProposalRepository
import org.vechain.indexer.safe.repository.SafeTxStateRepository

@ExtendWith(MockKExtension::class)
internal class SafeServiceTest {

    @MockK lateinit var membershipRepository: SafeMembershipRepository
    @MockK lateinit var txStateRepository: SafeTxStateRepository
    @MockK lateinit var proposalRepository: SafeTxProposalRepository

    private val safe = "0x1111111111111111111111111111111111111111"
    private val owner = "0xAAAA111111111111111111111111111111111111"
    private val txHash = "0x" + "a".repeat(64)

    private lateinit var service: SafeService

    @BeforeEach
    fun setUp() {
        service = SafeService(membershipRepository, txStateRepository, proposalRepository)
    }

    private fun membership(removed: Long? = null) =
        SafeMembership(
            id = SafeMembership.buildId(safe, owner),
            safe = safe.lowercase(),
            owner = owner.lowercase(),
            addedBlock = 5L,
            addedTimestamp = 500L,
            removedBlock = removed,
            removedTimestamp = removed?.let { it * 100L },
            blockId = "0xblock",
            blockNumber = removed ?: 5L,
            blockTimestamp = (removed ?: 5L) * 100L,
            version = if (removed != null) 2 else 1,
        )

    private fun txState(executed: Boolean = false, failed: Boolean = false) =
        SafeTxState(
            id = SafeTxState.buildId(safe, txHash),
            safe = safe.lowercase(),
            txHash = txHash.lowercase(),
            approvers =
                mutableListOf(
                    SafeTxApproval(
                        owner = owner.lowercase(),
                        block = 10L,
                        blockTimestamp = 1000L,
                        vechainTxId = "0xchaintx",
                    )
                ),
            executed = executed,
            failed = failed,
            executor = if (executed) owner.lowercase() else null,
            executedBlock = if (executed) 12L else null,
            executedTimestamp = if (executed) 1200L else null,
            vechainTxId = if (executed) "0xexec" else "0xchaintx",
            blockId = "0xblock",
            blockNumber = if (executed) 12L else 10L,
            blockTimestamp = if (executed) 1200L else 1000L,
            version = 1,
        )

    private fun proposal() =
        SafeTxProposal(
            id = SafeTxProposal.buildId(safe, txHash),
            safe = safe.lowercase(),
            txHash = txHash.lowercase(),
            proposer = owner.lowercase(),
            proposedBlock = 8L,
            proposedTimestamp = 800L,
            proposedVechainTxId = "0xchainprop",
            to = "0x2222222222222222222222222222222222222222",
            value = BigInteger.ZERO,
            data = "0x",
            operation = 0,
            nonce = BigInteger.ONE,
            description = "test",
            envelopeRecorded = true,
            blockId = "0xblock",
            blockNumber = 8L,
            blockTimestamp = 800L,
            version = 1,
        )

    @Test
    fun `getSafesForOwner ALL queries findByOwner`() {
        val pageableSlot = slot<Pageable>()
        every { membershipRepository.findByOwner(owner.lowercase(), capture(pageableSlot)) } returns
            SliceImpl(listOf(membership()))

        val pageable = PageRequest.of(0, 20)
        val result: Slice<*> = service.getSafesForOwner(owner, SafeMembershipScope.ALL, pageable)

        assertEquals(1, result.content.size)
        verify(exactly = 1) { membershipRepository.findByOwner(owner.lowercase(), pageable) }
    }

    @Test
    fun `getSafesForOwner CURRENT queries findByOwnerAndRemovedBlockIsNull`() {
        every {
            membershipRepository.findByOwnerAndRemovedBlockIsNull(owner.lowercase(), any())
        } returns SliceImpl(listOf(membership()))

        val result =
            service.getSafesForOwner(owner, SafeMembershipScope.CURRENT, PageRequest.of(0, 20))

        assertEquals(1, result.content.size)
        assertNull(result.content.single().removedBlock)
    }

    @Test
    fun `getSafesForOwner PAST queries findByOwnerAndRemovedBlockIsNotNull`() {
        every {
            membershipRepository.findByOwnerAndRemovedBlockIsNotNull(owner.lowercase(), any())
        } returns SliceImpl(listOf(membership(removed = 9L)))

        val result =
            service.getSafesForOwner(owner, SafeMembershipScope.PAST, PageRequest.of(0, 20))

        assertEquals(1, result.content.size)
        assertEquals(9L, result.content.single().removedBlock)
    }

    @Test
    fun `getTxState returns the doc when present`() {
        val id = SafeTxState.buildId(safe, txHash)
        every { txStateRepository.findById(id) } returns Optional.of(txState(executed = true))

        val result = service.getTxState(safe, txHash)

        assertTrue(result.executed)
        assertFalse(result.failed)
        assertEquals(safe.lowercase(), result.safe)
        assertEquals(txHash.lowercase(), result.txHash)
    }

    @Test
    fun `getTxState returns an empty placeholder doc when missing`() {
        val id = SafeTxState.buildId(safe, txHash)
        every { txStateRepository.findById(id) } returns Optional.empty()

        val result = service.getTxState(safe, txHash)

        assertFalse(result.executed)
        assertEquals(0, result.approvers.size)
        assertNull(result.executor)
        assertEquals(0L, result.blockNumber)
        assertEquals(0, result.version)
        assertEquals(safe.lowercase(), result.safe)
        assertEquals(txHash.lowercase(), result.txHash)
    }

    @Test
    fun `listProposals queries findBySafe with normalised safe address`() {
        val pageableSlot = slot<Pageable>()
        every { proposalRepository.findBySafe(safe.lowercase(), capture(pageableSlot)) } returns
            SliceImpl(listOf(proposal()))

        val result = service.listProposals(safe, PageRequest.of(0, 20))

        assertEquals(1, result.content.size)
        val mapped = result.content.single()
        assertEquals(safe.lowercase(), mapped.safe)
        assertEquals(txHash.lowercase(), mapped.txHash)
        assertNotNull(mapped.value)
        assertNotNull(mapped.nonce)
        verify(exactly = 1) { proposalRepository.findBySafe(safe.lowercase(), any()) }
    }
}
