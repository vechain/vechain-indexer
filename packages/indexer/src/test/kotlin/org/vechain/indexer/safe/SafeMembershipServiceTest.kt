package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex

@ExtendWith(MockKExtension::class)
internal class SafeMembershipServiceTest {

    @MockK lateinit var repository: SafeWriteRepository

    private val safe = "0x1111111111111111111111111111111111111111"
    private val ownerA = "0xaaaa111111111111111111111111111111111111"
    private val ownerB = "0xbbbb222222222222222222222222222222222222"
    private val known = setOf(safe)

    private lateinit var service: SafeMembershipService

    @BeforeEach
    fun setUp() {
        every { repository.findCurrentMemberships(any()) } returns emptyList()
        service = SafeMembershipService(repository)
    }

    private fun event(
        type: String,
        owners: List<String>,
        blockNumber: Long,
        address: String = safe,
    ): IndexedEvent =
        buildIndexedEvent(
            blockId = toHex(blockNumber, 64),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 100,
            eventType = type,
            address = address,
            params =
                AbiEventParameters(
                    returnValues =
                        if (type == SafeMembershipService.SAFE_SETUP) mapOf("owners" to owners)
                        else mapOf("owner" to owners.single())
                ),
        )

    private fun setup(owners: List<String>, block: Long = 10L) =
        event(SafeMembershipService.SAFE_SETUP, owners, block)

    private fun added(owner: String, block: Long = 11L, address: String = safe) =
        event(SafeMembershipService.ADDED_OWNER, listOf(owner), block, address)

    private fun removed(owner: String, block: Long = 12L) =
        event(SafeMembershipService.REMOVED_OWNER, listOf(owner), block)

    private fun membership(owner: String, block: Long, removedBlock: Long? = null) =
        SafeMembership(
            id = SafeMembership.buildId(safe, owner),
            safe = safe,
            owner = owner,
            addedBlock = block,
            addedTimestamp = block * 100,
            removedBlock = removedBlock,
            removedTimestamp = removedBlock?.times(100),
            blockId = toHex(block, 64),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    @Test
    fun `a setup opens one membership per owner and an add opens another`() {
        val rows =
            service.processEvents(listOf(setup(listOf(ownerA, ownerB)), added(ownerB)), known)

        assertEquals(3, rows.size)
        assertEquals(listOf(ownerA, ownerB, ownerB), rows.map { it.owner })
        assertEquals(listOf(10L, 10L, 11L), rows.map { it.blockNumber })
        assertEquals(SafeMembership.buildId(safe, ownerA), rows[0].id)
        assertNull(rows[0].removedBlock)
    }

    @Test
    fun `a removal ends the membership the schema holds and a re-add restarts it`() {
        every { repository.findCurrentMemberships(any()) } returns
            listOf(membership(ownerA, block = 10))

        val removedRow = service.processEvents(listOf(removed(ownerA)), known).single()
        assertEquals(10L, removedRow.addedBlock)
        assertEquals(12L, removedRow.removedBlock)
        assertEquals(1200L, removedRow.removedTimestamp)

        every { repository.findCurrentMemberships(any()) } returns
            listOf(membership(ownerA, block = 10, removedBlock = 12))
        val reAdded = service.processEvents(listOf(added(ownerA, block = 14)), known).single()
        assertEquals(14L, reAdded.addedBlock)
        assertNull(reAdded.removedBlock)
        assertNull(reAdded.removedTimestamp)
    }

    @Test
    fun `an add and a removal in one block collapse into the block's last state`() {
        val rows = service.processEvents(listOf(added(ownerA, 20), removed(ownerA, 20)), known)

        assertEquals(1, rows.size)
        assertEquals(20L, rows[0].addedBlock)
        assertEquals(20L, rows[0].removedBlock)
    }

    @Test
    fun `a removal in a later block of one entry keeps the block the owner was added in`() {
        val rows = service.processEvents(listOf(added(ownerA, 11), removed(ownerA, 12)), known)

        assertEquals(listOf(11L, 12L), rows.map { it.blockNumber })
        assertEquals(11L, rows.last().addedBlock)
        assertEquals(12L, rows.last().removedBlock)
    }

    @Test
    fun `an event from an address the factory never deployed is dropped`() {
        val stranger = "0x9999999999999999999999999999999999999999"

        assertEquals(
            emptyList<SafeMembership>(),
            service.processEvents(listOf(added(ownerA, address = stranger)), known),
        )
        assertEquals(emptyList<SafeMembership>(), service.processEvents(emptyList(), known))
    }
}
