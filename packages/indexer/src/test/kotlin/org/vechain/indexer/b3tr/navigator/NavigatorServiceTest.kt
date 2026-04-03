package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
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

    @MockK lateinit var citizenRepository: NavigatorCitizenRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorService

    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        service =
            NavigatorService(
                repository,
                citizenRepository,
                mongoTemplate,
                inlineVersioningProperties,
            )
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
        assertEquals("50000", nav.stake)
        assertEquals(0, nav.citizenCount)
        assertEquals("0", nav.totalDelegated)
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
            listOf(event("B3TR_StakeAdded", mapOf("navigator" to "0xnav1", "newTotal" to "75000"))),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("75000", updated[0].stake)
    }

    @Test
    fun `StakeAdded falls back to arithmetic when newTotal is missing`() {
        val existing = navigatorFixture("0xnav1", stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(event("B3TR_StakeAdded", mapOf("navigator" to "0xnav1", "amount" to "10000"))),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("60000", updated[0].stake)
    }

    @Test
    fun `StakeWithdrawn updates stake to remaining`() {
        val existing = navigatorFixture("0xnav1", stake = "75000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event("B3TR_StakeWithdrawn", mapOf("navigator" to "0xnav1", "remaining" to "50000"))
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("50000", updated[0].stake)
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
                        "effectiveRound" to "6",
                    ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(NavigatorStatus.EXITING, updated[0].status)
        assertEquals("5", updated[0].exitAnnouncedRound)
        assertEquals("6", updated[0].exitEffectiveRound)
    }

    @Test
    fun `ExitFinalized sets status to DEACTIVATED and stake to 0`() {
        val existing = navigatorFixture("0xnav1", status = NavigatorStatus.EXITING, stake = "50000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(event("B3TR_ExitFinalized", mapOf("navigator" to "0xnav1"))),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(NavigatorStatus.DEACTIVATED, updated[0].status)
        assertEquals("0", updated[0].stake)
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
            listOf(event("B3TR_NavigatorDeactivated", mapOf("navigator" to "0xnav1"))),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(NavigatorStatus.DEACTIVATED, updated[0].status)
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
                    mapOf("navigator" to "0xnav1", "remainingStake" to "45000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("45000", updated[0].stake)
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
        assertEquals("150000", updated[0].totalDelegated)
    }

    @Test
    fun `DelegationUpdated adjusts totalDelegated using citizen old amount`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 2, totalDelegated = "100000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)
        every { citizenRepository.findById("0xcit1") } returns
            java.util.Optional.of(citizenFixture("0xcit1", amount = "40000"))

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationUpdated",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1", "newAmount" to "70000"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        // delta = 70000 - 40000 = 30000, totalDelegated = 100000 + 30000 = 130000
        assertEquals("130000", updated[0].totalDelegated)
        assertEquals(2, updated[0].citizenCount)
    }

    @Test
    fun `DelegationRemoved decrements citizenCount and subtracts citizen amount`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 3, totalDelegated = "150000")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)
        every { citizenRepository.findById("0xcit1") } returns
            java.util.Optional.of(citizenFixture("0xcit1", amount = "50000"))

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationRemoved",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(2, updated[0].citizenCount)
        assertEquals("100000", updated[0].totalDelegated)
    }

    @Test
    fun `DelegationRemoved does not go below zero`() {
        val existing = navigatorFixture("0xnav1", citizenCount = 0, totalDelegated = "0")
        every { repository.findById("0xnav1") } returns java.util.Optional.of(existing)
        every { citizenRepository.findById("0xcit1") } returns java.util.Optional.empty()

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                event(
                    "B3TR_DelegationRemoved",
                    mapOf("navigator" to "0xnav1", "citizen" to "0xcit1"),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(0, updated[0].citizenCount)
        assertEquals("0", updated[0].totalDelegated)
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
        assertEquals("50000", nav.stake)
        assertEquals(2, nav.citizenCount)
        assertEquals("30000", nav.totalDelegated)
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private fun citizenFixture(address: String, amount: String = "50000") =
        NavigatorCitizen(
            address = address,
            version = 1,
            blockId = "block-0",
            blockNumber = 50L,
            blockTimestamp = 500L,
            navigator = "0xnav1",
            amount = amount,
            delegatedAt = 500L,
            active = true,
        )

    private fun navigatorFixture(
        address: String,
        status: NavigatorStatus = NavigatorStatus.ACTIVE,
        stake: String = "50000",
        citizenCount: Int = 0,
        totalDelegated: String = "0",
        metadataURI: String? = null,
    ) =
        Navigator(
            address = address,
            version = 1,
            blockId = "block-0",
            blockNumber = 50L,
            blockTimestamp = 500L,
            status = status,
            stake = stake,
            citizenCount = citizenCount,
            totalDelegated = totalDelegated,
            metadataURI = metadataURI,
            registeredAt = 500L,
            exitAnnouncedRound = null,
            exitEffectiveRound = null,
            lastReportRound = null,
            lastReportURI = null,
        )
}
