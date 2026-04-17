package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
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
    @MockK lateinit var overviewSummaryRepository: NavigatorOverviewSummaryRepository
    @MockK lateinit var feeSummaryRepository: NavigatorFeeSummaryRepository

    private lateinit var service: NavigatorApiService

    private val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "blockTimestamp"))

    @BeforeEach
    fun setUp() {
        service =
            NavigatorApiService(mongoTemplate, overviewSummaryRepository, feeSummaryRepository)
    }

    @Test
    fun `findNavigators with no filters returns all navigators`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        val result = service.findNavigators(pageable = pageable)

        assertTrue(querySlot.captured.queryObject.isEmpty())
        assertFalse(result.hasNext())
    }

    @Test
    fun `findNavigators filters by navigator address in lowercase`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        service.findNavigators(navigator = "0xNAV1", pageable = pageable)

        assertEquals("0xnav1", querySlot.captured.queryObject["address"])
    }

    @Test
    fun `findNavigators filters by status list`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns emptyList()

        service.findNavigators(
            statuses = listOf(NavigatorStatus.ACTIVE, NavigatorStatus.EXITING),
            pageable = pageable,
        )

        @Suppress("UNCHECKED_CAST")
        val statusFilter = querySlot.captured.queryObject["status"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val inValues = statusFilter["\$in"] as List<String>
        assertEquals(listOf("ACTIVE", "EXITING"), inValues)
    }

    @Test
    fun `findNavigators detects hasNext when extra record returned`() {
        val querySlot = slot<Query>()
        every { mongoTemplate.find(capture(querySlot), Navigator::class.java) } returns
            (1..11).map { i -> navFixture("0xnav$i") }

        val result = service.findNavigators(pageable = pageable)

        assertTrue(result.hasNext())
        assertEquals(10, result.content.size)
    }

    @Test
    fun `getOverview reads precomputed global summary`() {
        every { overviewSummaryRepository.findById(NavigatorOverviewSummary.GLOBAL_ID) } returns
            java.util.Optional.of(
                NavigatorOverviewSummary(
                    id = NavigatorOverviewSummary.GLOBAL_ID,
                    version = 2,
                    blockId = "b",
                    blockNumber = 10L,
                    blockTimestamp = 10L,
                    recordType = NavigatorOverviewSummaryRecordType.GLOBAL_SUMMARY,
                    activeNavigators = 3L,
                    totalStaked = BigDecimal("125000"),
                    totalCitizens = 8L,
                    totalDelegated = BigDecimal("300000"),
                )
            )

        val overview = service.getOverview()

        assertEquals(3L, overview.activeNavigators)
        assertEquals(BigInteger("125000"), overview.totalStaked)
        assertEquals(8L, overview.totalCitizens)
        assertEquals(BigInteger("300000"), overview.totalDelegated)
    }

    @Test
    fun `getOverview returns zeros when summary is missing`() {
        every { overviewSummaryRepository.findById(NavigatorOverviewSummary.GLOBAL_ID) } returns
            java.util.Optional.empty()

        val overview = service.getOverview()

        assertEquals(0L, overview.activeNavigators)
        assertEquals(BigInteger.ZERO, overview.totalStaked)
        assertEquals(0L, overview.totalCitizens)
        assertEquals(BigInteger.ZERO, overview.totalDelegated)
    }

    @Test
    fun `getFeeSummary reads navigator specific summary`() {
        every {
            feeSummaryRepository.findById(NavigatorFeeSummaryDocument.navigatorSummaryId("0xnav1"))
        } returns
            java.util.Optional.of(
                NavigatorFeeSummaryDocument(
                    id = NavigatorFeeSummaryDocument.navigatorSummaryId("0xnav1"),
                    version = 1,
                    blockId = "b",
                    blockNumber = 5L,
                    blockTimestamp = 5L,
                    recordType = NavigatorFeeSummaryRecordType.NAVIGATOR_SUMMARY,
                    navigator = "0xnav1",
                    totalEarned = BigDecimal("250"),
                    totalClaimed = BigDecimal("100"),
                )
            )

        val summary = service.getFeeSummary("0xNav1")

        assertEquals(BigInteger("250"), summary.totalEarned)
        assertEquals(BigInteger("100"), summary.totalClaimed)
    }

    private fun navFixture(address: String) =
        Navigator(
            address = address,
            version = 1,
            blockId = "b",
            blockNumber = 1L,
            blockTimestamp = 1L,
            status = NavigatorStatus.ACTIVE,
            stake = BigDecimal("50000"),
            citizenCount = 0,
            totalDelegated = BigDecimal.ZERO,
            metadataURI = null,
            registeredAt = 1L,
            exitAnnouncedRound = null,
            exitEffectiveDeadline = null,
            exitEffectiveDeadlineBlock = null,
            lastReportRound = null,
            lastReportURI = null,
        )
}
