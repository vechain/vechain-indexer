package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class NavigatorServiceTest {
    @MockK lateinit var repository: NavigatorRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorService

    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        service = NavigatorService(repository, mongoTemplate, inlineVersioningProperties)
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<Navigator> =
        VersionedDocumentAccumulator(service::findByAddress)

    private fun event(eventType: String, params: Map<String, Any>): IndexedEvent =
        buildIndexedEvent(
            eventType = eventType,
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
            params = AbiEventParameters(params, eventType),
        )

    // ============================================================================
    // Registration
    // ============================================================================

    @Test
    fun `NavigatorRegistered creates new navigator with ACTIVE status`() {
        every { repository.findById("0xnav1") } returns java.util.Optional.empty()

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorRegistered",
                    mapOf(
                        "navigator" to "0xNav1",
                        "stakeAmount" to "50000",
                        "metadataURI" to "ipfs://meta",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(1, updated.size)
        val nav = updated[0]
        assertEquals("0xnav1", nav.address)
        assertEquals(NavigatorStatus.ACTIVE, nav.status)
        assertEquals(BigDecimal("50000"), nav.stake)
        assertEquals(0, nav.citizenCount)
        assertEquals(BigDecimal.ZERO, nav.totalDelegated)
        assertEquals("ipfs://meta", nav.metadataURI)
        assertEquals(1000L, nav.registeredAt)
        assertEquals(1, nav.version)
    }

    // ============================================================================
    // Staking
    // ============================================================================

    @Test
    fun `StakeAdded updates stake to newTotal`() {
        val existing = navigatorFixture("0xnav1", stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_StakeAdded",
                    mapOf("navigator" to "0xnav1", "amount" to "25000", "newTotal" to "75000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("75000"), updated[0].stake)
    }

    @Test
    fun `StakeAdded fails fast when required params are missing`() {
        val existing = navigatorFixture("0xnav1", stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        val exception =
            assertThrows(IllegalStateException::class.java) {
                service.processBlockEvents(
                    listOf(
                        event(
                            "B3TR_StakeAdded",
                            mapOf("navigator" to "0xnav1", "amount" to "10000"),
                        )
                    ),
                    block,
                    acc,
                )
            }

        assertTrue(exception.message!!.contains("Missing param 'newTotal'"))
    }

    @Test
    fun `StakeWithdrawn updates stake to remaining`() {
        val existing = navigatorFixture("0xnav1", stake = "75000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_StakeWithdrawn",
                    mapOf("navigator" to "0xnav1", "amount" to "25000", "remaining" to "50000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("50000"), updated[0].stake)
    }

    // ============================================================================
    // Exit
    // ============================================================================

    @Test
    fun `ExitAnnounced sets status to EXITING`() {
        val existing = navigatorFixture("0xnav1")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_ExitAnnounced",
                    mapOf(
                        "navigator" to "0xnav1",
                        "announcedAtRound" to "5",
                        "effectiveDeadline" to "6",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(NavigatorStatus.EXITING, updated[0].status)
        assertEquals("5", updated[0].exitAnnouncedRound)
        assertEquals("6", updated[0].exitEffectiveDeadline)
    }

    // ============================================================================
    // Deactivation & Slashing
    // ============================================================================

    @Test
    fun `NavigatorDeactivated sets status to DEACTIVATED`() {
        val existing = navigatorFixture("0xnav1")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorDeactivated",
                    mapOf("navigator" to "0xnav1", "slashPercentage" to "100"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(NavigatorStatus.DEACTIVATED, updated[0].status)
    }

    @Test
    fun `checkExpiredExits only updates expired exiting navigators`() {
        val existing =
            navigatorFixture(
                "0xnav1",
                status = NavigatorStatus.EXITING,
                citizenCount = 2,
                totalDelegated = "90000",
                exitEffectiveDeadlineBlock = 100L,
            )
        every {
            repository.findByStatusAndExitEffectiveDeadlineBlockLessThanEqual(
                NavigatorStatus.EXITING,
                100L,
            )
        } returns listOf(existing)
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()

        service.checkExpiredExits(block, acc)

        val (updated, _) = acc.results()
        assertEquals(1, updated.size)
        assertEquals(NavigatorStatus.DEACTIVATED, updated[0].status)
        assertEquals(0, updated[0].citizenCount)
        assertEquals(BigDecimal.ZERO, updated[0].totalDelegated)
    }

    @Test
    fun `NavigatorSlashed updates stake to remainingStake`() {
        val existing = navigatorFixture("0xnav1", stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorSlashed",
                    mapOf(
                        "navigator" to "0xnav1",
                        "amount" to "5000",
                        "remainingStake" to "45000",
                        "reason" to "missing-report",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("45000"), updated[0].stake)
    }

    @Test
    fun `NavigatorMinorSlashed updates stake to remainingStake`() {
        val existing = navigatorFixture("0xnav1", stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorMinorSlashed",
                    mapOf(
                        "navigator" to "0xnav1",
                        "amount" to "5000",
                        "remainingStake" to "45000",
                        "roundId" to "3",
                        "infractionFlags" to "7",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("45000"), updated[0].stake)
    }

    // ============================================================================
    // Metadata & Reports
    // ============================================================================

    @Test
    fun `MetadataURIUpdated updates metadataURI`() {
        val existing = navigatorFixture("0xnav1", metadataURI = "ipfs://old")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_MetadataURIUpdated",
                    mapOf("navigator" to "0xnav1", "newURI" to "ipfs://new"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("ipfs://new", updated[0].metadataURI)
    }

    @Test
    fun `ReportSubmitted updates lastReportRound and lastReportURI`() {
        val existing = navigatorFixture("0xnav1")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_ReportSubmitted",
                    mapOf("navigator" to "0xnav1", "roundId" to "3", "reportURI" to "ipfs://report"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("3", updated[0].lastReportRound)
        assertEquals("ipfs://report", updated[0].lastReportURI)
    }

    // ============================================================================
    // Delegations
    // ============================================================================

    @Test
    fun `DelegationCreated increments citizenCount and adds to totalDelegated`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 2, totalDelegated = "100000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationCreated",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "amount" to "50000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(3, updated[0].citizenCount)
        assertEquals(BigDecimal("150000"), updated[0].totalDelegated)
    }

    @Test
    fun `DelegationIncreased adds addedAmount to totalDelegated`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 2, totalDelegated = "100000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationIncreased",
                    mapOf(
                        "navigator" to "0xnav1",
                        "citizen" to "0xcit1",
                        "addedAmount" to "30000",
                        "newTotal" to "70000",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("130000"), updated[0].totalDelegated)
        assertEquals(2, updated[0].citizenCount)
    }

    @Test
    fun `DelegationDecreased subtracts removedAmount from totalDelegated`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 2, totalDelegated = "100000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationDecreased",
                    mapOf(
                        "navigator" to "0xnav1",
                        "citizen" to "0xcit1",
                        "removedAmount" to "20000",
                        "newTotal" to "20000",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("80000"), updated[0].totalDelegated)
        assertEquals(2, updated[0].citizenCount)
    }

    @Test
    fun `DelegationRemoved decrements citizenCount and subtracts amount`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 3, totalDelegated = "150000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationRemoved",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "amount" to "50000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(2, updated[0].citizenCount)
        assertEquals(BigDecimal("100000"), updated[0].totalDelegated)
    }

    @Test
    fun `DelegationRemoved does not go below zero`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 0, totalDelegated = "0")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationRemoved",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "amount" to "0"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(0, updated[0].citizenCount)
        assertEquals(BigDecimal.ZERO, updated[0].totalDelegated)
    }

    // ============================================================================
    // No-op events
    // ============================================================================

    @Test
    fun `NavigatorVoteCast does not create or update navigator state`() {
        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorVoteCast",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "roundId" to "1"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(0, updated.size)
    }

    @Test
    fun `FeeDeposited does not create or update navigator state`() {
        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_FeeDeposited",
                    mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "100"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(0, updated.size)
    }

    // ============================================================================
    // Multiple events in same block
    // ============================================================================

    @Test
    fun `multiple events for same navigator in same block are processed correctly`() {
        every { repository.findById("0xnav1") } returns java.util.Optional.empty()

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_NavigatorRegistered",
                    mapOf(
                        "navigator" to "0xnav1",
                        "stakeAmount" to "50000",
                        "metadataURI" to "ipfs://v1",
                    ),
                ),
                event(
                    "B3TR_DelegationCreated",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "amount" to "10000"),
                ),
                event(
                    "B3TR_DelegationCreated",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit2", "amount" to "20000"),
                ),
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(1, updated.size)
        val nav = updated[0]
        assertEquals("0xnav1", nav.address)
        assertEquals(NavigatorStatus.ACTIVE, nav.status)
        assertEquals(BigDecimal("50000"), nav.stake)
        assertEquals(2, nav.citizenCount)
        assertEquals(BigDecimal("30000"), nav.totalDelegated)
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private fun navigatorFixture(
        address: String,
        status: NavigatorStatus = NavigatorStatus.ACTIVE,
        stake: String = "50000",
        citizenCount: Int = 0,
        totalDelegated: String = "0",
        metadataURI: String? = null,
        exitEffectiveDeadlineBlock: Long? = null,
    ) =
        Navigator(
            address = address,
            version = 1,
            blockId = "block-0",
            blockNumber = 50L,
            blockTimestamp = 500L,
            status = status,
            stake = BigDecimal(stake),
            citizenCount = citizenCount,
            totalDelegated = BigDecimal(totalDelegated),
            metadataURI = metadataURI,
            registeredAt = 500L,
            exitAnnouncedRound = null,
            exitEffectiveDeadline = null,
            exitEffectiveDeadlineBlock = exitEffectiveDeadlineBlock,
            lastReportRound = null,
            lastReportURI = null,
        )
}
