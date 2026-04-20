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
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class NavigatorFeeServiceTest {
    @MockK lateinit var repository: NavigatorFeeRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private lateinit var service: NavigatorFeeService
    private val block = BlockDetails("block-1", 100L, 1000L)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findById(any()) } returns java.util.Optional.empty()
        service = NavigatorFeeService(repository, mongoTemplate, inlineVersioningProperties)
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<NavigatorFee> =
        VersionedDocumentAccumulator(service::findById)

    @Test
    fun `fee events store roundId as int and sort field remains numeric`() {
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
                            mapOf("navigator" to "0xnav1", "roundId" to "10", "amount" to "100"),
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
                            mapOf("navigator" to "0xnav1", "roundId" to "10", "amount" to "100"),
                            "B3TR_FeeClaimed",
                        ),
                ),
            ),
            block,
            accumulator,
        )

        val fee = accumulator.results().first.single()
        assertEquals(10, fee.roundId)
        assertEquals("0xnav1_10", fee.id)
        assertEquals(14L, fee.unlockRound)
        assertEquals(true, fee.claimed)
    }
}
