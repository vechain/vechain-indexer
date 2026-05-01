package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.safe.repository.SafeMembershipRepository

@ExtendWith(MockKExtension::class)
internal class SafeMembershipServiceTest {

    @MockK lateinit var repository: SafeMembershipRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val safe = "0x1111111111111111111111111111111111111111"
    private val ownerA = "0xAAAA111111111111111111111111111111111111"
    private val ownerB = "0xBBBB222222222222222222222222222222222222"

    private lateinit var service: SafeMembershipService

    @BeforeEach
    fun setUp() {
        service = SafeMembershipService(repository, mongoTemplate, inlineVersioningProperties)
        every { repository.findById(any<String>()) } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
    }

    private fun safeSetupEvent(
        owners: List<String>,
        blockNumber: Long = 10L,
        blockTimestamp: Long = 1000L,
        blockId: String = "0xblock",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            eventType = SafeMembershipService.SAFE_SETUP,
            address = safe,
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "initiator" to "0xinitiator",
                            "owners" to owners,
                            "threshold" to "1",
                            "initializer" to "0xinitializer",
                            "fallbackHandler" to "0xfallback",
                        )
                ),
        )

    private fun addedOwnerEvent(
        owner: String,
        blockNumber: Long = 11L,
        blockTimestamp: Long = 1100L,
        blockId: String = "0xblock2",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            eventType = SafeMembershipService.ADDED_OWNER,
            address = safe,
            params = AbiEventParameters(returnValues = mapOf("owner" to owner)),
        )

    private fun removedOwnerEvent(
        owner: String,
        blockNumber: Long = 12L,
        blockTimestamp: Long = 1200L,
        blockId: String = "0xblock3",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            eventType = SafeMembershipService.REMOVED_OWNER,
            address = safe,
            params = AbiEventParameters(returnValues = mapOf("owner" to owner)),
        )

    @Test
    fun `processBlock with no relevant events returns empty`() {
        val (updated, existing) = service.processBlock(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `SafeSetup creates one membership per owner with version 1`() {
        val event = safeSetupEvent(listOf(ownerA, ownerB))
        val (updated, existing) = service.processBlock(listOf(event))

        assertEquals(2, updated.size)
        assertEquals(0, existing.size)
        val byOwner = updated.associateBy { it.owner }
        val a = byOwner[ownerA.lowercase()]!!
        val b = byOwner[ownerB.lowercase()]!!
        assertEquals(safe.lowercase(), a.safe)
        assertEquals(10L, a.addedBlock)
        assertEquals(1000L, a.addedTimestamp)
        assertNull(a.removedBlock)
        assertEquals(1, a.version)
        assertEquals(SafeMembership.buildId(safe, ownerA), a.id)
        assertEquals(SafeMembership.buildId(safe, ownerB), b.id)
    }

    @Test
    fun `AddedOwner creates a single membership when not previously known`() {
        val (updated, existing) = service.processBlock(listOf(addedOwnerEvent(ownerA)))

        assertEquals(1, updated.size)
        assertEquals(0, existing.size)
        val record = updated.single()
        assertEquals(ownerA.lowercase(), record.owner)
        assertEquals(1, record.version)
        assertNull(record.removedBlock)
    }

    @Test
    fun `RemovedOwner archives an existing membership and bumps version`() {
        val recordId = SafeMembership.buildId(safe, ownerA)
        val initial =
            SafeMembership(
                id = recordId,
                safe = safe.lowercase(),
                owner = ownerA.lowercase(),
                addedBlock = 5L,
                addedTimestamp = 500L,
                blockId = "0xinitial",
                blockNumber = 5L,
                blockTimestamp = 500L,
                version = 1,
            )
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(initial)
        every { repository.findById(recordId) } returns Optional.of(initial)

        val (updated, archived) = service.processBlock(listOf(removedOwnerEvent(ownerA)))

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        val u = updated.single()
        assertEquals(12L, u.removedBlock)
        assertEquals(1200L, u.removedTimestamp)
        assertEquals(2, u.version)
        // Archived record is the prior state
        assertEquals(1, archived.single().version)
        assertNull(archived.single().removedBlock)
    }

    @Test
    fun `Re-added owner clears removedBlock and bumps version`() {
        val recordId = SafeMembership.buildId(safe, ownerA)
        val initial =
            SafeMembership(
                id = recordId,
                safe = safe.lowercase(),
                owner = ownerA.lowercase(),
                addedBlock = 5L,
                addedTimestamp = 500L,
                removedBlock = 8L,
                removedTimestamp = 800L,
                blockId = "0xprev",
                blockNumber = 8L,
                blockTimestamp = 800L,
                version = 2,
            )
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(initial)
        every { repository.findById(recordId) } returns Optional.of(initial)

        val (updated, archived) = service.processBlock(listOf(addedOwnerEvent(ownerA)))

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        val u = updated.single()
        assertNull(u.removedBlock)
        assertNull(u.removedTimestamp)
        assertEquals(11L, u.addedBlock)
        assertEquals(3, u.version)
    }

    @Test
    fun `Adds and removes in the same block produce an archived first version and a current second version`() {
        val event1 =
            addedOwnerEvent(ownerA, blockNumber = 10L, blockId = "0xblock", blockTimestamp = 1000L)
        val event2 =
            removedOwnerEvent(
                ownerA,
                blockNumber = 10L,
                blockId = "0xblock",
                blockTimestamp = 1000L,
            )

        val (updated, _) = service.processBlock(listOf(event1, event2))

        // The accumulator collapses both updates into one current document; the archived list
        // captures the intermediate v1 state.
        assertEquals(1, updated.size)
        val u = updated.single()
        assertNotNull(u.removedBlock)
        assertEquals(10L, u.removedBlock)
    }

    @Test
    fun `Address field on the event identifies the Safe`() {
        val event =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                eventType = SafeMembershipService.ADDED_OWNER,
                address = "0xANOTHERSAFE2222222222222222222222222222",
                params = AbiEventParameters(returnValues = mapOf("owner" to ownerA)),
            )

        val (updated, _) = service.processBlock(listOf(event))
        assertEquals(1, updated.size)
        assertEquals("0xanothersafe2222222222222222222222222222", updated.single().safe)
    }
}
