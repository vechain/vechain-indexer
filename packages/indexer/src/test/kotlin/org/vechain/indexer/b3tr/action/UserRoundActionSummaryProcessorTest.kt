package org.vechain.indexer.b3tr.action

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
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class UserRoundActionSummaryProcessorTest {

    private class TestableProcessor(
        repository: UserRoundActionSummaryRepository,
        mongoTemplate: MongoTemplate,
        service: UserRoundActionSummaryService,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
        b3trRoundService: B3trRoundService,
    ) :
        UserRoundActionSummaryProcessor(
            repository = repository,
            mongoTemplate = mongoTemplate,
            service = service,
            checkpointService = checkpointService,
            processorMetrics = processorMetrics,
            b3trRoundService = b3trRoundService,
        ) {
        fun readRoundId(): Int? = roundId
    }

    @MockK lateinit var repository: UserRoundActionSummaryRepository

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var service: UserRoundActionSummaryService

    @MockK lateinit var checkpointService: CheckpointService

    @MockK lateinit var b3trRoundService: B3trRoundService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var processor: TestableProcessor

    private fun reward(id: String, blockDetails: BlockDetails) =
        buildIndexedEvent(
            id = id,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            eventType = "B3TR_ActionReward",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "appId" to "app-1",
                            "receiver" to "user-1",
                            "amount" to "10000000000000000000",
                            "action" to "",
                            "distributor" to "0x0",
                        )
                ),
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
                        mapOf(
                            "cycle" to cycle,
                            "totalAmount" to "10000000000000000000",
                            "distributor" to "0x0",
                        )
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
                service = service,
                checkpointService = checkpointService,
                processorMetrics = processorMetrics,
                b3trRoundService = b3trRoundService,
            )
    }

    @Nested
    inner class WhenContractResolvesRound {

        @BeforeEach
        fun stubContract() {
            coEvery { b3trRoundService.getCurrentRound(any<BlockRevision>()) } returns 1
        }

        @Test
        fun `process empty events doesn't save any records`() {
            runBlocking {
                processor.process(IndexingResult.LogResult(100, emptyList(), Status.SYNCING))
            }

            verify(exactly = 0) { service.save(any(), any()) }
            verify(exactly = 0) { service.processEvents(any(), any()) }
        }

        @Test
        fun `process updated records and archives are saved`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(reward("e1", block))

            val updatedRecords =
                listOf(
                    UserRoundActionSummary(
                        version = 2,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        entity = "user-1",
                        entityType = EntityType.USER,
                        roundId = 1,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )
            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 1) } returns
                (updatedRecords to archiveRecords)
            every { service.save(updatedRecords, archiveRecords) } just Runs

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }

            verify(exactly = 1) { service.processEvents(events, 1) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
            assertEquals(1, processor.readRoundId())
        }

        @Test
        fun `EmissionDistributed slices the batch and advances roundId`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val rewardBefore = reward("before", block)
            val transition = emission("transition", block, cycle = "2")
            val rewardAfter = reward("after", block)
            val events = listOf(rewardBefore, transition, rewardAfter)

            val updatedBefore =
                listOf(
                    UserRoundActionSummary(
                        version = 1,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        entity = "user-1",
                        entityType = EntityType.USER,
                        roundId = 1,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )
            val updatedAfter = listOf(updatedBefore.first().copy(roundId = 2))

            every { service.processEvents(listOf(rewardBefore), 1) } returns
                (updatedBefore to emptyList())
            every { service.processEvents(listOf(rewardAfter), 2) } returns
                (updatedAfter to emptyList())
            every { service.save(any(), any()) } just Runs

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 1) { service.processEvents(listOf(rewardBefore), 1) }
            verify(exactly = 1) { service.processEvents(listOf(rewardAfter), 2) }
            verify(exactly = 1) { service.save(updatedBefore + updatedAfter, emptyList()) }
            assertEquals(2, processor.readRoundId())
        }

        @Test
        fun `only round change event advances roundId without invoking service`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(emission("transition", block, cycle = "2", v2 = true))

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 0) { service.processEvents(any(), any()) }
            verify(exactly = 0) { service.save(any(), any()) }
            assertEquals(2, processor.readRoundId())
        }
    }

    @Nested
    inner class InitialRoundResolution {

        @Test
        fun `initial roundId is resolved from B3trRoundService at firstBlock - 1`() {
            val block =
                BlockDetails(blockId = "block-100", blockNumber = 100L, blockTimestamp = 10L)
            val events = listOf(reward("e1", block))

            coEvery { b3trRoundService.getCurrentRound(BlockRevision.Number(99L)) } returns 7

            every { service.processEvents(events, roundId = 7) } returns
                (emptyList<UserRoundActionSummary>() to emptyList())

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block.blockNumber, events, Status.SYNCING)
                )
            }

            verify(exactly = 1) { service.processEvents(events, 7) }
            assertEquals(7, processor.readRoundId())
        }

        @Test
        fun `process fails fast when contract cannot resolve current round`() {
            val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events = listOf(reward("e1", block))

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

        @Test
        fun `initial roundId is only resolved once across multiple batches`() {
            val block1 = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val block2 = BlockDetails(blockId = "block-2", blockNumber = 2L, blockTimestamp = 20L)
            val events1 = listOf(reward("e1", block1))
            val events2 = listOf(reward("e2", block2))

            coEvery { b3trRoundService.getCurrentRound(any<BlockRevision>()) } returns 4
            every { service.processEvents(any(), 4) } returns
                (emptyList<UserRoundActionSummary>() to emptyList())

            runBlocking {
                processor.process(
                    IndexingResult.LogResult(block1.blockNumber, events1, Status.SYNCING)
                )
                processor.process(
                    IndexingResult.LogResult(block2.blockNumber, events2, Status.SYNCING)
                )
            }

            coVerify(exactly = 1) { b3trRoundService.getCurrentRound(any<BlockRevision>()) }
            verify(exactly = 2) { service.processEvents(any(), 4) }
        }
    }
}
