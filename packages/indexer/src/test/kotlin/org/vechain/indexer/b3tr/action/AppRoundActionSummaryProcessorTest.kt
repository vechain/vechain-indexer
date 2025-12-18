package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
internal class AppRoundActionSummaryProcessorTest {
    // A small testable subclass to expose protected methods where useful
    private class TestableProcessor(
        repository: AppRoundActionSummaryRepository,
        archiveService: ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
        service: AppRoundActionSummaryService,
        startRound: Int,
        indexerVersionService: IndexerVersionService,
    ) :
        AppRoundActionSummaryProcessor(
            repository = repository,
            appRoundActionSummaryArchiveService = archiveService,
            service = service,
            startRound = startRound,
            indexerVersionService = indexerVersionService,
        ) {
        fun readRoundId(): Int = roundId
    }

    @Nested
    inner class NoExistingRecord() {
        @MockK lateinit var repository: AppRoundActionSummaryRepository

        @MockK
        lateinit var archiveService:
            ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>

        @MockK lateinit var service: AppRoundActionSummaryService

        @MockK lateinit var indexerVersionService: IndexerVersionService

        private lateinit var processor: TestableProcessor

        @BeforeEach
        fun setUp() {
            MockKAnnotations.init(this)
            every { repository.findFirstByOrderByBlockNumberDesc() } returns null
            processor =
                TestableProcessor(
                    repository,
                    archiveService,
                    service = service,
                    startRound = 1,
                    indexerVersionService = indexerVersionService,
                )
        }

        @Test
        fun `process empty events doesn't save any records`() {
            runBlocking {
                processor.process(IndexingResult.EventsOnly(100, emptyList(), Status.SYNCING))
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
                    AppRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
                        user = "user-1",
                        appId = "app-1",
                        roundId = 1,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )

            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 1) } returns
                Triple(updatedRecords, archiveRecords, 1)
            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
            runBlocking {
                processor.process(
                    IndexingResult.EventsOnly(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }

            verify(exactly = 1) { service.processEvents(events, 1) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
        }

        @Test
        fun `process EmissionDistributed event should result in a roundId change`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val roundChangeEvent =
                buildIndexedEvent(
                    id = "e1",
                    blockId = blockDetailsEvent1.blockId,
                    blockNumber = blockDetailsEvent1.blockNumber,
                    blockTimestamp = blockDetailsEvent1.blockTimestamp,
                    eventType = "EmissionDistributed",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "cycle" to "2",
                                    "totalAmount" to "10000000000000000000",
                                    "distributor" to "0x0",
                                )
                        ),
                )
            val rewardEvent =
                buildIndexedEvent(
                    id = "e2",
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
            val events = listOf(roundChangeEvent, rewardEvent)

            val updatedRecords =
                listOf(
                    AppRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
                        user = "user-1",
                        appId = "app-1",
                        roundId = 2,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )

            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 1) } returns
                Triple(updatedRecords, archiveRecords, 2)
            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
            runBlocking {
                processor.process(
                    IndexingResult.EventsOnly(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }

            assertEquals(2, processor.readRoundId())
            verify(exactly = 1) { service.processEvents(events, 1) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
        }

        @Test
        fun `process EmissionDistributedV2 event should result in a roundId change`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val roundChangeEvent =
                buildIndexedEvent(
                    id = "e1",
                    blockId = blockDetailsEvent1.blockId,
                    blockNumber = blockDetailsEvent1.blockNumber,
                    blockTimestamp = blockDetailsEvent1.blockTimestamp,
                    eventType = "EmissionDistributedV2",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "cycle" to "2",
                                    "totalAmount" to "10000000000000000000",
                                    "distributor" to "0x0",
                                )
                        ),
                )
            val rewardEvent =
                buildIndexedEvent(
                    id = "e2",
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

            val events = listOf(roundChangeEvent, rewardEvent)

            val updatedRecords =
                listOf(
                    AppRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
                        user = "user-1",
                        appId = "app-1",
                        roundId = 2,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )
            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 1) } returns
                Triple(updatedRecords, archiveRecords, 2)
            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
            runBlocking {
                processor.process(
                    IndexingResult.EventsOnly(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }
            assertEquals(2, processor.readRoundId())
            verify(exactly = 1) { service.processEvents(events, 1) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
        }

        @Test
        fun `process only round change event and no reward event should result in a roundId change`() {
            val blockDetailsEvent1 =
                BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 10L)
            val roundChangeEvent =
                buildIndexedEvent(
                    id = "e1",
                    blockId = blockDetailsEvent1.blockId,
                    blockNumber = blockDetailsEvent1.blockNumber,
                    blockTimestamp = blockDetailsEvent1.blockTimestamp,
                    eventType = "EmissionDistributedV2",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "cycle" to "2",
                                    "totalAmount" to "10000000000000000000",
                                    "distributor" to "0x0",
                                )
                        ),
                )

            val events = listOf(roundChangeEvent)

            val updatedRecords = emptyList<AppRoundActionSummary>()
            val archiveRecords = emptyList<AppRoundActionSummary>()

            every { service.processEvents(events, roundId = 1) } returns
                Triple(updatedRecords, archiveRecords, 2)
            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify roundId is updated and empty results are saved
            runBlocking {
                processor.process(
                    IndexingResult.EventsOnly(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }
            assertEquals(2, processor.readRoundId())
            verify(exactly = 1) { service.processEvents(events, 1) }
            verify(exactly = 0) { service.save(updatedRecords, archiveRecords) }
        }
    }

    @Nested
    inner class ExistingRecord() {
        @MockK lateinit var repository: AppRoundActionSummaryRepository

        @MockK
        lateinit var archiveService:
            ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>

        @MockK lateinit var service: AppRoundActionSummaryService

        @MockK lateinit var indexerVersionService: IndexerVersionService

        private lateinit var processor: TestableProcessor

        @BeforeEach
        fun setUp() {
            MockKAnnotations.init(this)
            val latestRecord =
                AppRoundActionSummary(
                    version = 2,
                    blockId = "block-0",
                    blockNumber = 0L,
                    blockTimestamp = 0L,
                    appId = "app-3",
                    user = "user-3",
                    roundId = 5,
                    actionsRewarded = 10,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                )
            every { repository.findFirstByOrderByBlockNumberDesc() } returns latestRecord
            processor =
                TestableProcessor(
                    repository,
                    archiveService,
                    service = service,
                    startRound = 1,
                    indexerVersionService = indexerVersionService,
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
                    AppRoundActionSummary(
                        version = 2,
                        blockId = blockDetailsEvent1.blockId,
                        blockNumber = blockDetailsEvent1.blockNumber,
                        blockTimestamp = blockDetailsEvent1.blockTimestamp,
                        user = "user-1",
                        appId = "app-1",
                        roundId = 1,
                        actionsRewarded = 1,
                        totalRewardAmount = BigDecimal.ONE,
                        totalImpact = null,
                    )
                )

            val archiveRecords = listOf(updatedRecords.first().copy(version = 1))

            every { service.processEvents(events, roundId = 5) } returns
                Triple(updatedRecords, archiveRecords, 5)

            every { service.save(updatedRecords, archiveRecords) } just Runs

            // Verify that service.save is called with the correct parameters
            runBlocking {
                processor.process(
                    IndexingResult.EventsOnly(
                        events.maxOf { it.blockNumber },
                        events,
                        Status.SYNCING,
                    )
                )
            }

            verify(exactly = 1) { service.processEvents(events, 5) }
            verify(exactly = 1) { service.save(updatedRecords, archiveRecords) }
        }
    }
}
