package org.vechain.indexer.b3tr.navigator

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DEACTIVATED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_CREATED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_DECREASED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_INCREASED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_REMOVED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.EXIT_ANNOUNCED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.METADATA_UPDATED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.MINOR_SLASHED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.REGISTERED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.REPORT_SUBMITTED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.STAKE_ADDED
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class NavigatorServiceTest {
    @MockK lateinit var repository: NavigatorWriteRepository

    private lateinit var service: NavigatorService

    private val block = BlockDetails("0xblock", 100L, 1000L)
    private val nav = "0xaaaa111111111111111111111111111111111111"
    private val citizen = "0xcccc111111111111111111111111111111111111"
    private val other = "0xbbbb111111111111111111111111111111111111"

    @BeforeEach
    fun setUp() {
        every { repository.findExpiredExits(any()) } returns emptyList()
        every { repository.findCurrentNavigators(any(), any()) } returns emptyList()
        every { repository.findCurrentCitizens(any(), any()) } returns emptyList()
        every { repository.findActiveCitizens(any(), any()) } returns emptyList()
        service = NavigatorService(repository)
    }

    private fun event(type: String, vararg params: Pair<String, Any>): IndexedEvent =
        buildIndexedEvent(
            eventType = type,
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
            params = AbiEventParameters(mapOf(*params), type),
        )

    private fun navigator(
        address: String = nav,
        status: NavigatorStatus = NavigatorStatus.ACTIVE,
        citizens: Int = 0,
        delegated: String = "0",
        deadline: Long? = null,
    ) =
        Navigator(
            address = address,
            blockId = "0xold",
            blockNumber = 50L,
            blockTimestamp = 500L,
            status = status,
            stake = BigDecimal("50000"),
            citizenCount = citizens,
            totalDelegated = BigDecimal(delegated),
            metadataURI = "ipfs://meta",
            registeredAt = 500L,
            exitAnnouncedRound = deadline?.let { 5 },
            exitEffectiveDeadlineBlock = deadline,
            lastReportRound = null,
            lastReportURI = null,
        )

    private fun citizenOf(navigator: String, address: String = citizen, amount: String = "100") =
        NavigatorCitizen(
            address = address,
            blockId = "0xold",
            blockNumber = 50L,
            blockTimestamp = 500L,
            navigator = navigator,
            amount = BigDecimal(amount),
            delegatedAt = 500L,
            active = true,
        )

    @Test
    fun `a registration opens an active navigator at this block`() {
        val update =
            service.processBlock(
                block,
                listOf(
                    event(
                        REGISTERED,
                        "navigator" to nav.uppercase(),
                        "stakeAmount" to "50000",
                        "metadataURI" to "ipfs://meta",
                    )
                ),
            )

        val created = update.navigators.single()
        assertEquals(nav, created.address)
        assertEquals(NavigatorStatus.ACTIVE, created.status)
        assertEquals(BigDecimal("50000"), created.stake)
        assertEquals(0, created.citizenCount)
        assertEquals(1000L, created.registeredAt)
        assertEquals(100L, created.blockNumber)
        assertTrue(update.citizens.isEmpty())
    }

    @Test
    fun `events for a navigator the schema does not know are skipped`() {
        val update =
            service.processBlock(
                block,
                listOf(event(STAKE_ADDED, "navigator" to nav, "amount" to "1", "newTotal" to "2")),
            )

        assertTrue(update.isEmpty())
    }

    @Test
    fun `stake, metadata and report events rewrite the stored row at this block`() {
        every { repository.findCurrentNavigators(setOf(nav), any()) } returns listOf(navigator())

        val update =
            service.processBlock(
                block,
                listOf(
                    event(STAKE_ADDED, "navigator" to nav, "amount" to "1", "newTotal" to "75000"),
                    event(
                        MINOR_SLASHED,
                        "navigator" to nav,
                        "amount" to "5000",
                        "remainingStake" to "70000",
                        "roundId" to "7",
                        "infractionFlags" to "1",
                    ),
                    event(METADATA_UPDATED, "navigator" to nav, "newURI" to "ipfs://new"),
                    event(
                        REPORT_SUBMITTED,
                        "navigator" to nav,
                        "roundId" to "7",
                        "reportURI" to "ipfs://report",
                    ),
                ),
            )

        val row = update.navigators.single()
        assertEquals(BigDecimal("70000"), row.stake)
        assertEquals("ipfs://new", row.metadataURI)
        assertEquals("7", row.lastReportRoundValue)
        assertEquals("ipfs://report", row.lastReportURI)
        assertEquals(100L, row.blockNumber)
    }

    @Test
    fun `an exit announcement marks the navigator exiting until its deadline`() {
        every { repository.findCurrentNavigators(setOf(nav), any()) } returns listOf(navigator())

        val row =
            service
                .processBlock(
                    block,
                    listOf(
                        event(
                            EXIT_ANNOUNCED,
                            "navigator" to nav,
                            "announcedAtRound" to "5",
                            "effectiveDeadline" to "140",
                        )
                    ),
                )
                .navigators
                .single()

        assertEquals(NavigatorStatus.EXITING, row.status)
        assertEquals("5", row.exitAnnouncedRoundValue)
        assertEquals("140", row.exitEffectiveDeadline)
        assertEquals(140L, row.exitEffectiveDeadlineBlock)
    }

    @Test
    fun `an exit falling due deactivates the navigator and ends its citizens' delegations`() {
        every { repository.findExpiredExits(100L) } returns
            listOf(
                navigator(
                    status = NavigatorStatus.EXITING,
                    citizens = 1,
                    delegated = "100",
                    deadline = 100,
                )
            )
        every { repository.findActiveCitizens(setOf(nav), any()) } returns listOf(citizenOf(nav))

        val update = service.processBlock(block, emptyList())

        val row = update.navigators.single()
        assertEquals(NavigatorStatus.DEACTIVATED, row.status)
        assertEquals(0, row.citizenCount)
        assertEquals(BigDecimal.ZERO, row.totalDelegated)
        assertEquals(BigDecimal("50000"), row.stake)
        val ended = update.citizens.single()
        assertFalse(ended.active)
        assertEquals(100L, ended.blockNumber)
    }

    @Test
    fun `a deactivation event also ends a delegation created earlier in the block`() {
        every { repository.findCurrentNavigators(setOf(nav), any()) } returns
            listOf(navigator(citizens = 1, delegated = "100"))
        every { repository.findActiveCitizens(setOf(nav), any()) } returns listOf(citizenOf(nav))

        val update =
            service.processBlock(
                block,
                listOf(
                    event(
                        DELEGATION_CREATED,
                        "citizen" to other,
                        "navigator" to nav,
                        "amount" to "5",
                    ),
                    event(DEACTIVATED, "navigator" to nav, "slashPercentage" to "100"),
                ),
            )

        assertEquals(NavigatorStatus.DEACTIVATED, update.navigators.single().status)
        assertEquals(setOf(citizen, other), update.citizens.map { it.address }.toSet())
        assertTrue(update.citizens.none { it.active })
    }

    @Test
    fun `delegation events move the navigator's totals and the citizen's amount`() {
        every { repository.findCurrentNavigators(setOf(nav), any()) } returns
            listOf(navigator(citizens = 2, delegated = "300"))
        every { repository.findCurrentCitizens(setOf(citizen), any()) } returns
            listOf(citizenOf(nav))

        val update =
            service.processBlock(
                block,
                listOf(
                    event(
                        DELEGATION_INCREASED,
                        "citizen" to citizen,
                        "navigator" to nav,
                        "addedAmount" to "50",
                        "newTotal" to "150",
                    ),
                    event(
                        DELEGATION_DECREASED,
                        "citizen" to citizen,
                        "navigator" to nav,
                        "removedAmount" to "30",
                        "newTotal" to "120",
                    ),
                ),
            )

        assertEquals(BigDecimal("320"), update.navigators.single().totalDelegated)
        assertEquals(2, update.navigators.single().citizenCount)
        val row = update.citizens.single()
        assertEquals(BigDecimal("120"), row.amount)
        assertTrue(row.active)
        assertEquals(500L, row.delegatedAt)
    }

    @Test
    fun `a removal ends the delegation unless the citizen has already moved on`() {
        every { repository.findCurrentNavigators(setOf(nav), any()) } returns
            listOf(navigator(citizens = 1, delegated = "100"))
        every { repository.findCurrentCitizens(setOf(citizen), any()) } returns
            listOf(citizenOf(other))

        val update =
            service.processBlock(
                block,
                listOf(
                    event(
                        DELEGATION_REMOVED,
                        "citizen" to citizen,
                        "navigator" to nav,
                        "amount" to "100",
                    )
                ),
            )

        val row = update.navigators.single()
        assertEquals(nav, row.address)
        assertEquals(0, row.citizenCount)
        assertEquals(BigDecimal.ZERO, row.totalDelegated)
        assertTrue(update.citizens.isEmpty())
    }

    @Test
    fun `a block with no events and no exit due writes nothing`() {
        assertTrue(service.processBlock(block, emptyList()).isEmpty())
    }
}
