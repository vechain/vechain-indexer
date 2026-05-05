package org.vechain.indexer.b3tr.relayer

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.relayer.repository.AutoVotingToggleRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class AutoVotingToggleServiceTest {

    @MockK lateinit var repository: AutoVotingToggleRepository
    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    private val inlineVersioningProperties: InlineVersioningProperties =
        mockk(relaxed = true) {
            every { blockWindow } returns 10_000
            every { maxVersions } returns 100
            every { minVersions } returns 20
        }

    private lateinit var service: AutoVotingToggleService

    @BeforeEach
    fun setUp() {
        service = AutoVotingToggleService(repository, mongoTemplate, inlineVersioningProperties)
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
        every { repository.findById(any<String>()) } returns java.util.Optional.empty()
    }

    private fun event(
        id: String,
        block: Long,
        timestamp: Long = block * 10,
        account: String,
        enabled: Boolean,
    ) =
        buildIndexedEvent(
            id = id,
            blockId = "block-$block",
            blockNumber = block,
            blockTimestamp = timestamp,
            eventType = "AutoVotingToggled",
            params =
                AbiEventParameters(returnValues = mapOf("account" to account, "enabled" to enabled)),
        )

    @Test
    fun `processEvents writes one row per address with activeFromRound = roundId + 1`() {
        val events =
            listOf(
                event("e1", block = 100, account = "0xAaAa", enabled = true),
                event("e2", block = 101, account = "0xBb", enabled = false),
            )

        val (updated, archived) = service.processEvents(events, roundId = 7)

        assertTrue(archived.isEmpty())
        val byAddress = updated.associateBy { it.address }
        assertEquals(setOf("0xaaaa", "0xbb"), byAddress.keys)
        assertEquals(
            AutoVotingToggle(
                id = generateId("0xaaaa", "8"),
                address = "0xaaaa",
                enabled = true,
                activeFromRound = 8,
                blockId = "block-100",
                blockNumber = 100,
                blockTimestamp = 1000,
                version = 1,
            ),
            byAddress["0xaaaa"],
        )
        assertEquals(
            AutoVotingToggle(
                id = generateId("0xbb", "8"),
                address = "0xbb",
                enabled = false,
                activeFromRound = 8,
                blockId = "block-101",
                blockNumber = 101,
                blockTimestamp = 1010,
                version = 1,
            ),
            byAddress["0xbb"],
        )
    }

    @Test
    fun `multiple toggles for same address in same round collapse to last event`() {
        val events =
            listOf(
                event("e1", block = 100, account = "0xa", enabled = true),
                event("e2", block = 101, account = "0xa", enabled = false),
                event("e3", block = 102, account = "0xa", enabled = true),
            )

        val (updated, _) = service.processEvents(events, roundId = 5)

        assertEquals(1, updated.size)
        // Last event wins. activeFromRound = 6.
        val row = updated.single()
        assertEquals("0xa", row.address)
        assertEquals(true, row.enabled)
        assertEquals(6, row.activeFromRound)
        // Block fields reflect the final event.
        assertEquals(102L, row.blockNumber)
    }

    @Test
    fun `multiple toggles in same block collapse to last event`() {
        val events =
            listOf(
                event("e1", block = 100, account = "0xa", enabled = true),
                event("e2", block = 100, account = "0xa", enabled = false),
            )

        val (updated, _) = service.processEvents(events, roundId = 5)

        assertEquals(1, updated.size)
        assertEquals(false, updated.single().enabled)
    }

    @Test
    fun `processEvents ignores non-AutoVotingToggled events in the slice`() {
        val mixed =
            listOf(
                event("e1", block = 100, account = "0xAa", enabled = true),
                buildIndexedEvent(id = "x", eventType = "OtherEvent"),
            )

        val (updated, _) = service.processEvents(mixed, roundId = 3)

        assertEquals(1, updated.size)
        assertEquals("0xaa", updated.single().address)
    }

    @Test
    fun `processEvents fails fast when account is missing`() {
        val bad =
            buildIndexedEvent(
                id = "bad",
                eventType = "AutoVotingToggled",
                params = AbiEventParameters(returnValues = mapOf("enabled" to true)),
            )

        assertThrows(IllegalStateException::class.java) {
            service.processEvents(listOf(bad), roundId = 1)
        }
    }

    @Test
    fun `save skips when there are no rows to persist`() {
        service.save(emptyList(), emptyList())
        verify(exactly = 0) { mongoTemplate.bulkOps(any(), any<Class<*>>(), any<String>()) }
    }
}
