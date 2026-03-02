package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.Runs
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
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class UserRoundActionSummaryProcessorTest {

    // A small testable subclass to expose protected methods where useful
    private class TestableProcessor(
        repository: UserRoundActionSummaryRepository,
        mongoTemplate: MongoTemplate,
        service: UserRoundActionSummaryService,
        startRound: Int,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        UserRoundActionSummaryProcessor(
            repository = repository,
            mongoTemplate = mongoTemplate,
            service = service,
            startRound = startRound,
            checkpointService = checkpointService,
            processorMetrics = processorMetrics,
        ) {
        fun readRoundId(): Int = roundId
    }

    @Nested
    inner class NoExistingRecord() {
        @MockK lateinit var repository: UserRoundActionSummaryRepository

        @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

        @MockK lateinit var service: UserRoundActionSummaryService

        @MockK lateinit var checkpointService: CheckpointService

        private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

        private lateinit var processor: TestableProcessor

        @BeforeEach
        fun setUp() {
            MockKAnnotations.init(this)
            every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
            every { repository.findFirstByOrderByBlockNumberDesc() } returns null
            processor =
                TestableProcessor(
                    repository,
                    mongoTemplate,
                    service = service,
                    startRound = 1,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
        }

        @Test
        fun `process empty events doesn't save any records`() {
            runBlocking {
                processor.process(IndexingResult.LogResult(100, emptyList(), Status.SYNCING))
            }

            // Verify that service.save is not called
            verify(exactly = 0) { service.save(any(), any()) }

            // Verify that service.processEvents is not called
            verify(exactly = 0) { service.processEvents(any(), 1) }
        }

        @Test
        fun `process updated records and archives are saved`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events =
                listOf(
                    buildIndexedEvent(
                        id = "e1",
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
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
                )

            val updatedRecords =
                listOf(
                    UserRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
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
                (Triple(updatedRecords, archiveRecords, 2))
            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
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
            assertEquals(2, processor.readRoundId())
        }
    }

    @Nested
    inner class ExistingRecord() {
        @MockK lateinit var repository: UserRoundActionSummaryRepository

        @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

        @MockK lateinit var service: UserRoundActionSummaryService

        @MockK lateinit var checkpointService: CheckpointService

        private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

        private lateinit var processor: TestableProcessor

        @BeforeEach
        fun setUp() {
            MockKAnnotations.init(this)
            every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
            val latestRecord =
                UserRoundActionSummary(
                    version = 2,
                    blockId = "block-0",
                    blockNumber = 0L,
                    blockTimestamp = 0L,
                    entity = "user-3",
                    entityType = EntityType.USER,
                    roundId = 4,
                    actionsRewarded = 10,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                )

            every { repository.findFirstByOrderByBlockNumberDesc() } returns latestRecord
            processor =
                TestableProcessor(
                    repository,
                    mongoTemplate,
                    service = service,
                    startRound = 1,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
        }

        @Test
        fun `process should use the roundId from the latest record if one exists`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val events =
                listOf(
                    buildIndexedEvent(
                        id = "e1",
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
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
                )

            val updatedRecords =
                listOf(
                    UserRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
                        entity = "user-1",
                        entityType = EntityType.USER,
                        roundId = 1,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )

            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 4) } returns
                Triple(updatedRecords, archiveRecords, 4)

            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
            runBlocking {
                processor.process(
                    IndexingResult.LogResult(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }

            verify(exactly = 1) { service.processEvents(events, 4) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
            assertEquals(4, processor.readRoundId())
        }
    }
}
