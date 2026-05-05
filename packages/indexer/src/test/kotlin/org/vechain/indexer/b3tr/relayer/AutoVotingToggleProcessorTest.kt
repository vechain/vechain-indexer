package org.vechain.indexer.b3tr.relayer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.b3tr.relayer.repository.AutoVotingToggleRepository
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class AutoVotingToggleProcessorTest {

    private class TestableProcessor(
        repository: AutoVotingToggleRepository,
        mongoTemplate: MongoTemplate,
        service: AutoVotingToggleService,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
        b3trRoundService: B3trRoundService,
    ) :
        AutoVotingToggleProcessor(
            repository = repository,
            mongoTemplate = mongoTemplate,
            service = service,
            checkpointService = checkpointService,
            processorMetrics = processorMetrics,
            b3trRoundService = b3trRoundService,
        ) {
        fun readRoundId(): Int? = roundId
    }

    @MockK lateinit var repository: AutoVotingToggleRepository
    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var service: AutoVotingToggleService
    @MockK lateinit var checkpointService: CheckpointService
    @MockK lateinit var b3trRoundService: B3trRoundService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var processor: TestableProcessor

    private fun toggle(id: String, blockDetails: BlockDetails, account: String, enabled: Boolean) =
        buildIndexedEvent(
            id = id,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            eventType = "AutoVotingToggled",
            params =
                AbiEventParameters(returnValues = mapOf("account" to account, "enabled" to enabled)),
        )

    private fun emission(
        id: String,
        blockDetails: BlockDetails,
        cycle: String,
        v2: Boolean = false,
    ) =
        buildIndexedEvent(
            id = id,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            eventType = if (v2) "EmissionDistributedV2" else "EmissionDistributed",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf("cycle" to cycle, "totalAmount" to "1", "distributor" to "0x0")
                ),
        )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
        processor =
            TestableProcessor(
                repository,
                mongoTemplate,
                service,
                checkpointService,
                processorMetrics,
                b3trRoundService,
            )
    }

    @Nested
    inner class WhenContractResolvesRound {

        @BeforeEach
        fun stubContract() {
            coEvery { b3trRoundService.getCurrentRound(any<BlockRevision>()) } returns 5
        }

        @Test
        fun `empty events do not invoke service`() {
            runBlocking {
                processor.process(IndexingResult.LogResult(100, emptyList(), Status.SYNCING))
            }

            verify(exactly = 0) { service.processEvents(any(), any()) }
            verify(exactly = 0) { service.save(any(), any()) }
        }

        @Test
        fun `single toggle is persisted with the resolved round`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(toggle("e1", block, account = "0xA", enabled = true))

            val updated =
                listOf(
                    AutoVotingToggle(
                        id = "e1",
                        address = "0xa",
                        enabled = true,
                        activeFromRound = 6,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        version = 1,
                    )
                )

            every { service.processEvents(events, roundId = 5) } returns (updated to emptyList())
            every { service.save(updated, emptyList()) } just Runs

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 1) { service.processEvents(events, 5) }
            verify(exactly = 1) { service.save(updated, emptyList()) }
            assertEquals(5, processor.readRoundId())
        }

        @Test
        fun `EmissionDistributed slices a batch and tags toggles with the right round`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val before = toggle("before", block, "0xA", true)
            val transition = emission("transition", block, cycle = "6")
            val afterA = toggle("after-a", block, "0xA", false)
            val afterB = toggle("after-b", block, "0xB", true)
            val events = listOf(before, transition, afterA, afterB)

            val updatedBefore =
                listOf(
                    AutoVotingToggle(
                        id = "before",
                        address = "0xa",
                        enabled = true,
                        activeFromRound = 6,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        version = 1,
                    )
                )
            val updatedAfter =
                listOf(
                    AutoVotingToggle(
                        id = "after-a",
                        address = "0xa",
                        enabled = false,
                        activeFromRound = 7,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        version = 1,
                    ),
                    AutoVotingToggle(
                        id = "after-b",
                        address = "0xb",
                        enabled = true,
                        activeFromRound = 7,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        version = 1,
                    ),
                )

            every { service.processEvents(listOf(before), 5) } returns
                (updatedBefore to emptyList())
            every { service.processEvents(listOf(afterA, afterB), 6) } returns
                (updatedAfter to emptyList())
            every { service.save(any(), any()) } just Runs

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 1) { service.processEvents(listOf(before), 5) }
            verify(exactly = 1) { service.processEvents(listOf(afterA, afterB), 6) }
            verify(exactly = 1) { service.save(updatedBefore + updatedAfter, emptyList()) }
            assertEquals(6, processor.readRoundId())
        }

        @Test
        fun `only round transition advances roundId without invoking service`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(emission("transition", block, cycle = "9", v2 = true))

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 0) { service.processEvents(any(), any()) }
            verify(exactly = 0) { service.save(any(), any()) }
            assertEquals(9, processor.readRoundId())
        }
    }

    @Nested
    inner class InitialRoundResolution {

        @Test
        fun `initial roundId resolves once and reuses across batches`() {
            val block1 = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val block2 = BlockDetails(blockId = "block-2", blockNumber = 2L, blockTimestamp = 20L)
            val e1 = listOf(toggle("e1", block1, "0xA", true))
            val e2 = listOf(toggle("e2", block2, "0xB", true))

            coEvery { b3trRoundService.getCurrentRound(any<BlockRevision>()) } returns 4
            every { service.processEvents(any(), 4) } returns
                (emptyList<AutoVotingToggle>() to emptyList())

            runBlocking {
                processor.process(IndexingResult.LogResult(block1.blockNumber, e1, Status.SYNCING))
                processor.process(IndexingResult.LogResult(block2.blockNumber, e2, Status.SYNCING))
            }

            coVerify(exactly = 1) { b3trRoundService.getCurrentRound(any<BlockRevision>()) }
            verify(exactly = 2) { service.processEvents(any(), 4) }
        }

        @Test
        fun `process fails fast when contract cannot resolve current round`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(toggle("e1", block, "0xA", true))

            coEvery { b3trRoundService.getCurrentRound(any<BlockRevision>()) } returns null

            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                runBlocking {
                    processor.process(
                        IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                    )
                }
            }

            verify(exactly = 0) { service.processEvents(any(), any()) }
            assertEquals(null, processor.readRoundId())
        }
    }
}
