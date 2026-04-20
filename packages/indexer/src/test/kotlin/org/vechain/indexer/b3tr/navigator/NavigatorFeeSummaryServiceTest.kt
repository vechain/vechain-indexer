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
internal class NavigatorFeeSummaryServiceTest {
    @MockK lateinit var repository: NavigatorFeeSummaryRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorFeeSummaryService
    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findById(any()) } returns java.util.Optional.empty()
        service = NavigatorFeeSummaryService(repository, mongoTemplate, inlineVersioningProperties)
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<NavigatorFeeSummaryDocument> =
        VersionedDocumentAccumulator(service::findById)

    @Test
    fun `deposit and claim update global and navigator fee summaries`() {
        val accumulator = newAccumulator()
        accumulator.startBlock()

        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_FeeDeposited",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "100"),
                            "B3TR_FeeDeposited",
                        ),
                ),
                buildIndexedEvent(
                    eventType = "B3TR_FeeClaimed",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "40"),
                            "B3TR_FeeClaimed",
                        ),
                ),
            ),
            block,
            accumulator,
        )

        val results = accumulator.results().first.associateBy { it.id }
        assertEquals(BigDecimal("100"), results[NavigatorFeeSummaryDocument.GLOBAL_ID]?.totalEarned)
        assertEquals(BigDecimal("40"), results[NavigatorFeeSummaryDocument.GLOBAL_ID]?.totalClaimed)
        assertEquals(
            BigDecimal("100"),
            results[NavigatorFeeSummaryDocument.navigatorSummaryId("0xnav1")]?.totalEarned,
        )
    }

    @Test
    fun `deposit and claim only query each fee summary once`() {
        val accumulator = newAccumulator()
        accumulator.startBlock()

        service.processBlockEvents(
            listOf(
                buildIndexedEvent(
                    eventType = "B3TR_FeeDeposited",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "100"),
                            "B3TR_FeeDeposited",
                        ),
                ),
                buildIndexedEvent(
                    eventType = "B3TR_FeeClaimed",
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    params =
                        AbiEventParameters(
                            mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "40"),
                            "B3TR_FeeClaimed",
                        ),
                ),
            ),
            block,
            accumulator,
        )

        verify(exactly = 1) { repository.findById(NavigatorFeeSummaryDocument.GLOBAL_ID) }
        verify(exactly = 1) {
            repository.findById(NavigatorFeeSummaryDocument.navigatorSummaryId("0xnav1"))
        }
    }
}
