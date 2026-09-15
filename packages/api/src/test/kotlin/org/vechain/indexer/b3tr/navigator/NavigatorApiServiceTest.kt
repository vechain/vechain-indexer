package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
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
import org.springframework.data.domain.Sort.Direction

@ExtendWith(MockKExtension::class)
internal class NavigatorApiServiceTest {
    @MockK lateinit var repository: NavigatorReadRepository

    private lateinit var service: NavigatorApiService

    private val nav = "0xAAAA111111111111111111111111111111111111"
    private val citizen = "0xCCCC111111111111111111111111111111111111"

    @BeforeEach
    fun setUp() {
        service = NavigatorApiService(repository)
    }

    private fun pageable(field: String, direction: Direction = Direction.DESC) =
        PageRequest.of(1, 10, Sort.by(direction, field))

    private fun navigator(address: String) =
        Navigator(
            address = address,
            blockId = "0xb",
            blockNumber = 1L,
            blockTimestamp = 1L,
            status = NavigatorStatus.ACTIVE,
            stake = BigDecimal("50000"),
            citizenCount = 0,
            totalDelegated = BigDecimal.ZERO,
            metadataURI = null,
            registeredAt = 1L,
            exitAnnouncedRound = null,
            exitEffectiveDeadlineBlock = null,
            lastReportRound = null,
            lastReportURI = null,
        )

    @Test
    fun `navigators are paged one row past the page in the requested order`() {
        every { repository.findNavigators(any(), any(), any(), any(), any()) } returns
            (1..11).map { navigator("0x" + it.toString().padStart(40, '0')) }

        val page =
            service.findNavigators(
                listOf(NavigatorStatus.ACTIVE),
                NavigatorSort.STAKE,
                pageable("stake", Direction.ASC),
            )

        assertTrue(page.hasNext())
        assertEquals(10, page.content.size)
        verify {
            repository.findNavigators(
                listOf(NavigatorStatus.ACTIVE),
                NavigatorSort.STAKE,
                Direction.ASC,
                10,
                11,
            )
        }
    }

    @Test
    fun `addresses are normalised before the lookup`() {
        every { repository.findNavigator(nav.lowercase()) } returns navigator(nav.lowercase())
        every { repository.findDelegationEvents(any(), any(), any(), any(), any()) } returns
            emptyList()
        every { repository.findCitizens(any(), any(), any(), any()) } returns emptyList()
        every { repository.findFees(any(), any(), any(), any()) } returns emptyList()
        every { repository.feeSummary(any()) } returns
            NavigatorFeeSummary(BigInteger("250"), BigInteger("100"))

        assertEquals(nav.lowercase(), service.getNavigatorById(nav)?.address)
        assertFalse(
            service.findDelegationEvents(nav, citizen, pageable("blockTimestamp")).hasNext()
        )
        service.findCitizens(nav, pageable("delegatedAt"))
        service.findFeeHistory(nav, pageable("roundId", Direction.ASC))
        assertEquals(BigInteger("250"), service.getFeeSummary(nav).totalEarned)

        verify {
            repository.findDelegationEvents(
                nav.lowercase(),
                citizen.lowercase(),
                10,
                11,
                Direction.DESC,
            )
            repository.findCitizens(nav.lowercase(), 10, 11, Direction.DESC)
            repository.findFees(nav.lowercase(), 10, 11, Direction.ASC)
            repository.feeSummary(nav.lowercase())
        }
    }

    @Test
    fun `the global fee summary and the overview pass straight through`() {
        every { repository.feeSummary(null) } returns
            NavigatorFeeSummary(BigInteger.TWO, BigInteger.ONE)
        every { repository.overview() } returns
            NavigatorOverview(3L, BigInteger("125000"), 8L, BigInteger("300000"))

        assertEquals(BigInteger.ONE, service.getFeeSummary(null).totalClaimed)
        assertEquals(3L, service.getOverview().activeNavigators)
        assertEquals(BigInteger("300000"), service.getOverview().totalDelegated)
    }
}
