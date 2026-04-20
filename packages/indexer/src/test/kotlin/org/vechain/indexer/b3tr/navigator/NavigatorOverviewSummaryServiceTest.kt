package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
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
internal class NavigatorOverviewSummaryServiceTest {
    @MockK lateinit var repository: NavigatorOverviewSummaryRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorOverviewSummaryService
    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findById(any()) } returns java.util.Optional.empty()
        service =
            NavigatorOverviewSummaryService(repository, mongoTemplate, inlineVersioningProperties)
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<NavigatorOverviewSummary> =
        VersionedDocumentAccumulator(service::findById)

    @Test
    fun `register and delegation update global summary totals`() {
        val accumulator = newAccumulator()
        accumulator.startBlock()

        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_NavigatorRegistered",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "stakeAmount" to "50000"),
                            "B3TR_NavigatorRegistered",
                        ),
                ),
                buildIndexedEvent(
                    eventType = "B3TR_DelegationCreated",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "navigator" to "0xnav1",
                                "citizen" to "0xcit1",
                                "amount" to "25000",
                            ),
                            "B3TR_DelegationCreated",
                        ),
                ),
            ),
            block,
            accumulator,
        )

        val global =
            accumulator.results().first.first { it.id == NavigatorOverviewSummary.GLOBAL_ID }
        assertEquals(1L, global.activeNavigators)
        assertEquals(BigDecimal("50000"), global.totalStaked)
        assertEquals(1L, global.totalCitizens)
        assertEquals(BigDecimal("25000"), global.totalDelegated)
    }

    @Test
    fun `expired exit removes navigator contribution from global summary`() {
        val existingState =
            NavigatorOverviewSummary(
                id = NavigatorOverviewSummary.navigatorStateId("0xnav1"),
                version = 1,
                blockId = "block-0",
                blockNumber = 90L,
                blockTimestamp = 900L,
                recordType = NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE,
                navigator = "0xnav1",
                status = NavigatorStatus.EXITING,
                stake = BigDecimal("50000"),
                citizenCount = 2,
                delegatedTotal = BigDecimal("75000"),
                exitEffectiveDeadlineBlock = 100L,
            )
        val existingGlobal =
            NavigatorOverviewSummary(
                id = NavigatorOverviewSummary.GLOBAL_ID,
                version = 1,
                blockId = "block-0",
                blockNumber = 90L,
                blockTimestamp = 900L,
                recordType = NavigatorOverviewSummaryRecordType.GLOBAL_SUMMARY,
                activeNavigators = 1L,
                totalStaked = BigDecimal("50000"),
                totalCitizens = 2L,
                totalDelegated = BigDecimal("75000"),
            )
        every {
            repository.findByRecordTypeAndStatusAndExitEffectiveDeadlineBlockLessThanEqual(
                NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE,
                NavigatorStatus.EXITING,
                100L,
            )
        } returns listOf(existingState)
        every { repository.findById(existingState.id) } returns java.util.Optional.of(existingState)
        every { repository.findById(NavigatorOverviewSummary.GLOBAL_ID) } returns
            java.util.Optional.of(existingGlobal)

        val accumulator = newAccumulator()
        accumulator.startBlock()

        service.checkExpiredExits(block, accumulator)

        val results = accumulator.results().first.associateBy { it.id }
        assertEquals(NavigatorStatus.DEACTIVATED, results[existingState.id]?.status)
        assertEquals(0L, results[NavigatorOverviewSummary.GLOBAL_ID]?.activeNavigators)
        assertEquals(BigDecimal.ZERO, results[NavigatorOverviewSummary.GLOBAL_ID]?.totalDelegated)
    }

    @Test
    fun `register and delegation only query navigator state once`() {
        val accumulator = newAccumulator()
        accumulator.startBlock()

        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_NavigatorRegistered",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "stakeAmount" to "50000"),
                            "B3TR_NavigatorRegistered",
                        ),
                ),
                buildIndexedEvent(
                    eventType = "B3TR_DelegationCreated",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "navigator" to "0xnav1",
                                "citizen" to "0xcit1",
                                "amount" to "25000",
                            ),
                            "B3TR_DelegationCreated",
                        ),
                ),
            ),
            block,
            accumulator,
        )

        verify(exactly = 1) {
            repository.findById(NavigatorOverviewSummary.navigatorStateId("0xnav1"))
        }
    }
}
