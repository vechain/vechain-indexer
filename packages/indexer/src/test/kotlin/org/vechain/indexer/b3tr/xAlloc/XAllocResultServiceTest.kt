package org.vechain.indexer.b3tr.xAlloc

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class XAllocResultServiceTest {
    @MockK lateinit var repository: XAllocResultRepository

    @MockK lateinit var archiveService: ArchiveService<XAllocResult, XAllocResultArchive>

    private lateinit var service: XAllocResultService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = XAllocResultService(repository, archiveService)
    }

    @Test
    fun `processEvents should create new records if no existing records`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.TEN),
                            )
                    ),
            )

        val event2 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 2,
                                "appsIds" to listOf("app2"),
                                "voteWeights" to listOf(1),
                            )
                    ),
            )

        // Mock repository to return null for first lookup
        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Should be two new updated records and no archives
        assertEquals(2, updated.size)
        assertEquals(updated[0].version, 1)
        assertEquals(updated[1].version, 1)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents should update a record if exists`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockTimestamp = 2L,
                blockId = "block-2",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.TEN),
                            )
                    ),
            )

        // Mock repository to return an existing record
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 1,
                appId = "app1",
                voters = 1,
                totalVotes = BigInteger.ONE,
            )
        every { repository.findByIdOrNull(any()) } returns existing

        val (updated, archived) = service.processEvents(listOf(event1))

        // Should be one updated record and one archived
        assertEquals(1, updated.size)
        assertEquals(updated[0].version, 2)
        assertEquals(updated[0].voters, 2)
        assertEquals(updated[0].totalVotes, BigInteger.TEN + BigInteger.ONE)
        assertEquals(updated[0].blockNumber, 2L)
        assertEquals(updated[0].blockId, "block-2")
        assertEquals(updated[0].blockTimestamp, 2L)

        // Check the archived record is unchanged
        assertEquals(1, archived.size)
        assertEquals(archived[0], existing)
    }

    @Test
    fun `processEvents aggregates multiple votes within the same block and round`() {
        val e1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(5)),
                            )
                    ),
            )

        val e2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(7)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(e1, e2))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(1, u.version)
        assertEquals(2, u.voters)
        assertEquals(BigInteger.valueOf(12), u.totalVotes)
        assertEquals(10L, u.blockNumber)
        assertEquals("block-10", u.blockId)
        assertEquals(1000L, u.blockTimestamp)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents increments version and archives prior snapshot across blocks`() {
        val b1 =
            buildIndexedEvent(
                id = "b1",
                blockNumber = 1L,
                blockTimestamp = 11L,
                blockId = "block-1",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 42,
                                "appsIds" to listOf("myApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(3)),
                            )
                    ),
            )

        val b2 =
            buildIndexedEvent(
                id = "b2",
                blockNumber = 2L,
                blockTimestamp = 22L,
                blockId = "block-2",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 42,
                                "appsIds" to listOf("myApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(4)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(b1, b2))

        // Final updated aggregate
        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        assertEquals(2, u.voters)
        assertEquals(BigInteger.valueOf(7), u.totalVotes)
        assertEquals(2L, u.blockNumber)
        assertEquals("block-2", u.blockId)
        assertEquals(22L, u.blockTimestamp)

        // One archived snapshot (the v1 state from block 1)
        assertEquals(1, archived.size)
        val a = archived.first()
        assertEquals(1, a.version)
        assertEquals(1L, a.blockNumber)
        assertEquals("block-1", a.blockId)
        assertEquals(11L, a.blockTimestamp)
        assertEquals(1, a.voters)
        assertEquals(BigInteger.valueOf(3), a.totalVotes)
    }

    @Test
    fun `processEvents creates independent records across rounds and apps`() {
        val e1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(2)),
                            )
                    ),
            )

        val e2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app2"),
                                "voteWeights" to listOf(BigInteger.valueOf(3)),
                            )
                    ),
            )

        val e3 =
            buildIndexedEvent(
                id = "e3",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 2,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(4)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(e1, e2, e3))

        assertEquals(3, updated.size)
        // All are fresh records
        updated.forEach { assertEquals(1, it.version) }
        // Check values by composing a map (roundId, appId) -> totalVotes
        val totals = updated.associate { Pair(Pair(it.roundId, it.appId), it.totalVotes) }
        assertEquals(BigInteger.valueOf(2), totals[Pair(1, "app1")])
        assertEquals(BigInteger.valueOf(3), totals[Pair(1, "app2")])
        assertEquals(BigInteger.valueOf(4), totals[Pair(2, "app1")])
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents prefers in-memory cache over repository after first lookup`() {
        val b1 =
            buildIndexedEvent(
                id = "b1",
                blockNumber = 100L,
                blockTimestamp = 1001L,
                blockId = "block-100",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 9,
                                "appsIds" to listOf("cacheApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(1)),
                            )
                    ),
            )
        val b2 =
            buildIndexedEvent(
                id = "b2",
                blockNumber = 101L,
                blockTimestamp = 1002L,
                blockId = "block-101",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 9,
                                "appsIds" to listOf("cacheApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(2)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(b1, b2))

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        // Repository should be consulted only once (first resolution), second lookup uses cache
        verify(exactly = 1) { repository.findByIdOrNull(any()) }
    }
}
