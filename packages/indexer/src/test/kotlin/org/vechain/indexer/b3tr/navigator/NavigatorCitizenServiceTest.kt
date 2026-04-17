package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
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
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class NavigatorCitizenServiceTest {
    @MockK lateinit var repository: NavigatorCitizenRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorCitizenService

    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        service = NavigatorCitizenService(repository, mongoTemplate, inlineVersioningProperties)
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<NavigatorCitizen> =
        VersionedDocumentAccumulator(service::findByAddress)

    @Test
    fun `DelegationCreated creates active citizen record`() {
        every { repository.findById("0xcit1") } returns java.util.Optional.empty()

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_DelegationCreated",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "citizen" to "0xCit1",
                                "navigator" to "0xNav1",
                                "amount" to "50000",
                            ),
                            "B3TR_DelegationCreated",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(1, updated.size)
        val citizen = updated[0]
        assertEquals("0xcit1", citizen.address)
        assertEquals("0xnav1", citizen.navigator)
        assertEquals(BigDecimal("50000"), citizen.amount)
        assertEquals(1000L, citizen.delegatedAt)
        assertTrue(citizen.active)
    }

    @Test
    fun `DelegationIncreased updates amount to newTotal`() {
        val existing = citizenFixture("0xcit1", navigator = "0xnav1", amount = "50000")
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_DelegationIncreased",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "citizen" to "0xcit1",
                                "navigator" to "0xnav1",
                                "addedAmount" to "25000",
                                "newTotal" to "75000",
                            ),
                            "B3TR_DelegationIncreased",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("75000"), updated[0].amount)
        assertTrue(updated[0].active)
    }

    @Test
    fun `DelegationDecreased updates amount to newTotal`() {
        val existing = citizenFixture("0xcit1", navigator = "0xnav1", amount = "50000")
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_DelegationDecreased",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "citizen" to "0xcit1",
                                "navigator" to "0xnav1",
                                "removedAmount" to "20000",
                                "newTotal" to "30000",
                            ),
                            "B3TR_DelegationDecreased",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(BigDecimal("30000"), updated[0].amount)
        assertTrue(updated[0].active)
    }

    @Test
    fun `DelegationRemoved sets active to false`() {
        val existing = citizenFixture("0xcit1", navigator = "0xnav1")
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_DelegationRemoved",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("citizen" to "0xcit1", "navigator" to "0xnav1"),
                            "B3TR_DelegationRemoved",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertFalse(updated[0].active)
    }

    @Test
    fun `re-delegation after removal creates active record again`() {
        val existing =
            citizenFixture("0xcit1", navigator = "0xnav1", amount = "50000", active = false)
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_DelegationCreated",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "citizen" to "0xcit1",
                                "navigator" to "0xnav2",
                                "amount" to "30000",
                            ),
                            "B3TR_DelegationCreated",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals("0xnav2", updated[0].navigator)
        assertEquals(BigDecimal("30000"), updated[0].amount)
        assertTrue(updated[0].active)
    }

    @Test
    fun `ExitAnnounced annotates active citizens with deadline block`() {
        val existing = citizenFixture("0xcit1", navigator = "0xnav1")
        every { repository.findByNavigatorAndActive("0xnav1", true) } returns listOf(existing)
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_ExitAnnounced",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "effectiveDeadline" to "120"),
                            "B3TR_ExitAnnounced",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertEquals(120L, updated[0].navigatorExitEffectiveDeadlineBlock)
    }

    @Test
    fun `NavigatorDeactivated deactivates active citizens for navigator`() {
        val existing = citizenFixture("0xcit1", navigator = "0xnav1")
        every { repository.findByNavigatorAndActive("0xnav1", true) } returns listOf(existing)
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()
        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_NavigatorDeactivated",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1"),
                            "B3TR_NavigatorDeactivated",
                        ),
                )
            ),
            block,
            acc,
        )

        val (updated, _) = acc.results()
        assertFalse(updated[0].active)
    }

    @Test
    fun `processExpiredDelegations deactivates expired active citizens`() {
        val existing =
            citizenFixture(
                "0xcit1",
                navigator = "0xnav1",
                navigatorExitEffectiveDeadlineBlock = 100L,
            )
        every {
            repository.findByActiveAndNavigatorExitEffectiveDeadlineBlockLessThanEqual(true, 100L)
        } returns listOf(existing)
        every { repository.findById("0xcit1") } returns java.util.Optional.of(existing)

        val acc = newAccumulator()
        acc.startBlock()

        service.processExpiredDelegations(block, acc)

        val (updated, _) = acc.results()
        assertFalse(updated[0].active)
    }

    private fun citizenFixture(
        address: String,
        navigator: String = "0xnav1",
        amount: String = "50000",
        active: Boolean = true,
        navigatorExitEffectiveDeadlineBlock: Long? = null,
    ) =
        NavigatorCitizen(
            address = address,
            version = 1,
            blockId = "block-0",
            blockNumber = 50L,
            blockTimestamp = 500L,
            navigator = navigator,
            amount = BigDecimal(amount),
            delegatedAt = 500L,
            active = active,
            navigatorExitEffectiveDeadlineBlock = navigatorExitEffectiveDeadlineBlock,
        )
}
