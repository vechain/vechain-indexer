package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

@ExtendWith(MockKExtension::class)
internal class NavigatorApiServiceTest {
    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var service: NavigatorApiService

    private val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockTimestamp"))

    @BeforeEach
    fun setUp() {
        service = NavigatorApiService(mongoTemplate)
    }

    @Test
    fun `findNavigators with no filters returns all navigators`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        val result = service.findNavigators(pageable = pageable)

        val doc = querySlot.captured.queryObject
        assertTrue(doc.isEmpty())
        assertFalse(result.hasNext())
    }

    @Test
    fun `findNavigators filters by navigator address in lowercase`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        service.findNavigators(navigator = "0xNAV1", pageable = pageable)

        val doc = querySlot.captured.queryObject
        assertEquals("0xnav1", doc["address"])
    }

    @Test
    fun `findNavigators filters by status list`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        service.findNavigators(
            statuses = listOf(NavigatorStatus.ACTIVE, NavigatorStatus.EXITING),
            pageable = pageable,
        )

        val doc = querySlot.captured.queryObject
        @Suppress("UNCHECKED_CAST") val statusFilter = doc["status"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val inValues = statusFilter["\$in"] as List<String>
        assertEquals(2, inValues.size)
        assertTrue(inValues.contains("ACTIVE"))
        assertTrue(inValues.contains("EXITING"))
    }

    @Test
    fun `findNavigators combines navigator and status filters`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        service.findNavigators(
            navigator = "0xnav1",
            statuses = listOf(NavigatorStatus.ACTIVE),
            pageable = pageable,
        )

        val doc = querySlot.captured.queryObject
        assertEquals("0xnav1", doc["address"])
        @Suppress("UNCHECKED_CAST") val statusFilter = doc["status"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val inValues = statusFilter["\$in"] as List<String>
        assertEquals(listOf("ACTIVE"), inValues)
    }

    @Test
    fun `findNavigators detects hasNext when extra record returned`() {
        val querySlot = slot<Query>()
        val navigators =
            (1..11).map { i ->
                Navigator(
                    address = "0xnav$i",
                    version = 1,
                    blockId = "b",
                    blockNumber = i.toLong(),
                    blockTimestamp = i.toLong(),
                    status = NavigatorStatus.ACTIVE,
                    stake = "50000",
                    citizenCount = 0,
                    totalDelegated = "0",
                    metadataURI = null,
                    registeredAt = 1L,
                    exitAnnouncedRound = null,
                    exitEffectiveRound = null,
                    lastReportRound = null,
                    lastReportURI = null,
                )
            }
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns navigators

        val result = service.findNavigators(pageable = pageable)

        assertTrue(result.hasNext())
        assertEquals(10, result.content.size)
    }

    @Test
    fun `findNavigators returns hasNext false when exact page size`() {
        val querySlot = slot<Query>()
        val navigators =
            (1..10).map { i ->
                Navigator(
                    address = "0xnav$i",
                    version = 1,
                    blockId = "b",
                    blockNumber = i.toLong(),
                    blockTimestamp = i.toLong(),
                    status = NavigatorStatus.ACTIVE,
                    stake = "50000",
                    citizenCount = 0,
                    totalDelegated = "0",
                    metadataURI = null,
                    registeredAt = 1L,
                    exitAnnouncedRound = null,
                    exitEffectiveRound = null,
                    lastReportRound = null,
                    lastReportURI = null,
                )
            }
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns navigators

        val result = service.findNavigators(pageable = pageable)

        assertFalse(result.hasNext())
        assertEquals(10, result.content.size)
    }
}
