package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class AppRoundActionSummaryServiceTest {
    @MockK lateinit var repository: AppRoundActionSummaryRepository

    @MockK
    lateinit var archiveService: ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>

    @MockK lateinit var pruner: TargetedPruner<AppRoundActionSummary, AppRoundActionSummaryArchive>

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate
    @MockK(relaxed = true) lateinit var bulkOps: BulkOperations
    @MockK(relaxed = true) lateinit var converter: MongoConverter

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: AppRoundActionSummaryRepository,
        archive: ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
        pruner: TargetedPruner<AppRoundActionSummary, AppRoundActionSummaryArchive>,
        impactConfig: ActionImpactConfig = ActionImpactConfig(),
    ) : AppRoundActionSummaryService(repository, archive, pruner, impactConfig) {
        fun callCreateOrUpdateExisting(
            appId: String,
            receiverId: String,
            roundId: Int,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            existing: AppRoundActionSummary?,
            version: Int,
        ) =
            createOrUpdateExisting(
                appId,
                receiverId,
                roundId,
                events,
                blockDetails,
                existing,
                version,
            )
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { archiveService.mongoTemplate } returns mongoTemplate
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns bulkOps
        every { mongoTemplate.converter } returns converter
        service = TestableService(repository, archiveService, pruner)
    }

    @Test
    fun `processEvents with empty events returns empty lists`() {
        val (updated, archived, updatedRoundId) = service.processEvents(emptyList(), roundId = 1)
        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
        assertEquals(1, updatedRoundId)
    }

    @Test
    fun `processEvents no existing record results in new record being created`() {
        val event =
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

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "1")) } returns null

        val (updated, archived, updatedRoundId) = service.processEvents(listOf(event), roundId = 1)

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(1, updated.first().version)
        assertEquals(1L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(1, updated.first().roundId)

        assertEquals(0, archived.size)
        assertEquals(1, updatedRoundId)
    }

    @Test
    fun `processEvents multiple events with same appId and user result in a single new record`() {
        val event1 =
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

        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "1")) } returns null

        val (updated, archived, updatedRoundId) =
            service.processEvents(listOf(event1, event2), roundId = 1)

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(1, updated.first().version)
        assertEquals(1L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(1, updated.first().roundId)

        assertEquals(0, archived.size)
        assertEquals(1, updatedRoundId)
    }

    @Test
    fun `processEvents existing record results in record being updated`() {
        val event =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "1")) } returns
            AppRoundActionSummary(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 900L,
                user = "user-1",
                appId = "app-1",
                roundId = 1,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val (updated, archived, updatedRoundId) = service.processEvents(listOf(event), roundId = 1)

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(2, updated.first().version)
        assertEquals(1L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(1, updated.first().roundId)

        assertEquals(1, archived.size)
        assertEquals(1, archived.first().version)
        assertEquals("app-1", archived.first().appId)
        assertEquals(0L, archived.first().blockNumber)
        assertEquals(0, archived.first().totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(1, archived.first().roundId)
        assertEquals(1, updatedRoundId)
    }

    @Test
    fun `processEvents throws if all events are not of type B3TR_ActionReward`() {
        val event1 =
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
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-2",
                blockNumber = 2L,
                eventType = "SomeOtherEvent",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        assertThrows<IllegalStateException> { service.processEvents(listOf(event1, event2), 1) }
    }

    @Test
    fun `process EmissionDistributed event should result in a roundId change`() {
        val blockDetails = BlockDetails("block-1", 1L, 10L)
        val roundChangeEvent =
            buildIndexedEvent(
                id = "e1",
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
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

        val events = listOf(roundChangeEvent, rewardEvent)

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "2")) } returns null

        val (updated, archive, updatedRoundId) = service.processEvents(events, 1)

        verify(exactly = 1) { repository.findByIdOrNull(generateId("app-1", "user-1", "2")) }

        assertEquals(1, updated.size)
        val record = updated.first()
        assertEquals("app-1", record.appId)
        assertEquals("user-1", record.user)
        assertEquals(1, record.version)
        assertEquals(blockDetails.blockNumber, record.blockNumber)
        assertEquals(0, record.totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(2, record.roundId)
        assertEquals(emptyList<AppRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
    }

    @Test
    fun `process EmissionDistributedV2 event should result in a roundId change`() {
        val blockDetails = BlockDetails("block-1", 1L, 10L)
        val roundChangeEvent =
            buildIndexedEvent(
                id = "e1",
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
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

        val events = listOf(roundChangeEvent, rewardEvent)

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "2")) } returns null

        val (updated, archive, updatedRoundId) = service.processEvents(events, 1)

        verify(exactly = 1) { repository.findByIdOrNull(generateId("app-1", "user-1", "2")) }

        assertEquals(1, updated.size)
        val record = updated.first()
        assertEquals("app-1", record.appId)
        assertEquals("user-1", record.user)
        assertEquals(1, record.version)
        assertEquals(blockDetails.blockNumber, record.blockNumber)
        assertEquals(0, record.totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(2, record.roundId)
        assertEquals(emptyList<AppRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
    }

    @Test
    fun `process only round change events updates round but produces no summaries`() {
        val blockDetails = BlockDetails("block-1", 1L, 10L)
        val roundChangeEvent =
            buildIndexedEvent(
                id = "e1",
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
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

        val (updated, archive, updatedRoundId) = service.processEvents(listOf(roundChangeEvent), 1)

        verify(exactly = 0) { repository.findByIdOrNull(any()) }
        assertEquals(emptyList<AppRoundActionSummary>(), updated)
        assertEquals(emptyList<AppRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
    }

    @Test
    fun `save applies updates and archives when lists are non empty`() {
        val updated =
            listOf(
                AppRoundActionSummary(
                    version = 2,
                    blockId = "b2",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 3,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                    roundId = 1,
                )
            )
        val archived =
            listOf(
                AppRoundActionSummary(
                    version = 1,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 1,
                    totalRewardAmount = BigDecimal.ONE,
                    totalImpact = null,
                    roundId = 1,
                )
            )

        every { archiveService.saveAll(archived) } just runs

        service.save(updated, archived)

        verify(exactly = 1) { bulkOps.execute() }
        verify(exactly = 1) { archiveService.saveAll(archived) }
    }

    @Test
    fun `save with empty lists does not call repositories`() {
        service.save(emptyList(), emptyList())
        verify(exactly = 0) { bulkOps.execute() }
        verify(exactly = 0) { archiveService.saveAll(any<List<AppRoundActionSummary>>()) }
    }

    @Test
    fun `createOrUpdateExisting with no existing record creates new one`() {
        val blockDetails = BlockDetails("block-1", 1L, 1000L)

        val event =
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

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )

        assertEquals("app-1", result.appId)
        assertEquals("user-1", result.user)
        assertEquals(1, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(1, result.roundId)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
        val blockDetails = BlockDetails("block-1", 1L, 1000L)

        val event =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        val existing =
            AppRoundActionSummary(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 900L,
                user = "user-1",
                appId = "app-1",
                roundId = 1,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event),
                blockDetails = blockDetails,
                existing = existing,
                version = existing.version + 1,
            )

        assertEquals("app-1", result.appId)
        assertEquals("user-1", result.user)
        assertEquals(2, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(1, result.roundId)
    }

    @Test
    fun `createOrUpdateExisting throws if events have different blockId, appId or receiverId`() {
        val blockDetails = BlockDetails("block-1", 1L, 1000L)

        val event1 =
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

        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-2",
                blockNumber = 2L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        val event3 =
            buildIndexedEvent(
                id = "e3",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-2",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        val event4 =
            buildIndexedEvent(
                id = "e4",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-2",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event1, event2),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event1, event3),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event1, event4),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )
        }
    }

    @Test
    fun `createOrUpdateExisting sums impacts correctly`() {
        val blockDetails = BlockDetails("block-1", 1L, 1000L)
        val event1 =
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
                                "proof" to
                                    "{\"version\": 2,\"description\": \"proof test\",\"impact\": {\"carbon\": 105,\"water\": 200}}",
                            )
                    ),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-1",
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                                "proof" to
                                    "{\"version\": 2,\"description\": \"proof 2\",\"impact\": {\"carbon\": 55,\"water\": 150,\"energy\": 70}}",
                            )
                    ),
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                roundId = 1,
                events = listOf(event1, event2),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )

        assertEquals("app-1", result.appId)
        assertEquals("user-1", result.user)
        assertEquals(1, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(1, result.roundId)
        assertEquals(160, result.totalImpact?.carbon)
        assertEquals(350, result.totalImpact?.water)
        assertEquals(70, result.totalImpact?.energy)
    }
}
