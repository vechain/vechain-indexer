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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class UserAllTimeActionSummaryProcessorTest {
    @MockK lateinit var repository: UserAllTimeActionSummaryRepository

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var service: UserAllTimeActionSummaryService

    @MockK lateinit var checkpointService: CheckpointService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var processor: UserAllTimeActionSummaryProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
        processor =
            UserAllTimeActionSummaryProcessor(
                repository = repository,
                mongoTemplate = mongoTemplate,
                service = service,
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
        verify(exactly = 0) { service.processEvents(any()) }
    }

    @Test
    fun `process updated records and archives are saved`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    blockId = "block-1",
                    blockNumber = 1L,
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
                UserAllTimeActionSummary(
                    version = 2,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1L,
                    entity = "user-1",
                    entityType = EntityType.USER,
                    actionsRewarded = 1,
                    totalRewardAmount = BigDecimal.ONE,
                    totalImpact = null,
                )
            )

        val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

        every { service.processEvents(events) } returns (updatedRecords to archiveRecords)
        every { service.save(updatedRecords, archiveRecords) } just Runs

        // Verify that service.save is called with the correct parameters
        runBlocking {
            processor.process(
                IndexingResult.LogResult(events.maxOf { it.blockNumber }, events, Status.SYNCING)
            )
        }

        verify(exactly = 1) { service.processEvents(events) }
        verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
    }
}
