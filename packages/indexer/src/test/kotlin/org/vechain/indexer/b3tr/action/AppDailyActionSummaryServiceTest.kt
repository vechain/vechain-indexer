package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class AppDailyActionSummaryServiceTest {
    @MockK lateinit var repository: AppDailyActionSummaryRepository

    @MockK
    lateinit var archiveService: ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive>

    @MockK lateinit var pruner: TargetedPruner<AppDailyActionSummary, AppDailyActionSummaryArchive>

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: AppDailyActionSummaryRepository,
        archive: ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive>,
        pruner: TargetedPruner<AppDailyActionSummary, AppDailyActionSummaryArchive>,
        mongoTemplate: MongoTemplate,
    ) : AppDailyActionSummaryService(repository, archive, pruner, mongoTemplate) {
        fun callResolveExisting(recordId: String, cache: Map<String, AppDailyActionSummary>) =
            resolveExisting(recordId, cache)

        fun callCreateOrUpdateExisting(
            appId: String,
            receiverId: String,
            date: String,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            existing: AppDailyActionSummary?,
        ) = createOrUpdateExisting(appId, receiverId, date, events, blockDetails, existing)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, archiveService, pruner, mongoTemplate)
    }

    @Test
    fun `processEvents with empty events returns empty lists`() {
        val (updated, archived) = service.processEvents(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents same appId and receiver but same day results in record update`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1757449050, // 2025-09-09
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
                blockTimestamp = 1757449060, // 2025-09-09
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

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(2, updated.first().version)
        assertEquals(2L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals("2025-09-09", updated.first().date)

        assertEquals(1, archived.size)
        assertEquals(1, archived.first().version)
        assertEquals("app-1", archived.first().appId)
        assertEquals(1L, archived.first().blockNumber)
        assertEquals(0, archived.first().totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals("2025-09-09", archived.first().date)
    }

    @Test
    fun `processEvents same appId and receiver but different day results in new records`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1757449050, // 2025-09-09
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
                blockTimestamp = 1757535450, // 2025-09-10
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

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        assertEquals(2, updated.size)

        val firstRecord = updated.find { it.blockId == "block-1" }!!
        assertEquals("app-1", firstRecord.appId)
        assertEquals("user-1", firstRecord.user)
        assertEquals(1, firstRecord.version)
        assertEquals(1L, firstRecord.blockNumber)
        assertEquals(0, firstRecord.totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals("2025-09-09", firstRecord.date)

        val secondRecord = updated.find { it.blockId == "block-2" }!!
        assertEquals("app-1", secondRecord.appId)
        assertEquals("user-1", secondRecord.user)
        assertEquals(1, secondRecord.version)
        assertEquals(2L, secondRecord.blockNumber)
        assertEquals(0, secondRecord.totalRewardAmount.compareTo(BigDecimal(20)))
        assertEquals("2025-09-10", secondRecord.date)

        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents updates if there is an existing record`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-2",
                blockNumber = 2L,
                blockTimestamp = 1757449060, // 2025-09-09
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

        every { repository.findByIdOrNull(generateId("app-1", "user-1", "2025-09-09")) } returns
            AppDailyActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
                date = "2025-09-09",
            )
        val (updated, archived) = service.processEvents(listOf(event1))

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(2, updated.first().version)
        assertEquals(2L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(11)))

        assertEquals(1, archived.size)
        assertEquals(1, archived.first().version)
        assertEquals("app-1", archived.first().appId)
        assertEquals(1L, archived.first().blockNumber)
        assertEquals(0, archived.first().totalRewardAmount.compareTo(BigDecimal(1)))
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

        assertThrows<IllegalStateException> { service.processEvents(listOf(event1, event2)) }
    }

    @Test
    fun `save applies updates and archives when lists are non empty`() {
        val updated =
            listOf(
                AppDailyActionSummary(
                    version = 2,
                    blockId = "b2",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 3,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                    date = "2025-09-09",
                )
            )

        val archived =
            listOf(
                AppDailyActionSummary(
                    version = 1,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 1,
                    totalRewardAmount = BigDecimal.ONE,
                    totalImpact = null,
                    date = "2025-09-09",
                )
            )

        // Mock bulkOps
        val mockBulkOps =
            mockk<org.springframework.data.mongodb.core.BulkOperations>(relaxed = true)

        every { mongoTemplate.bulkOps(any(), AppDailyActionSummary::class.java) } returns
            mockBulkOps

        // Mock BulkWriteResult
        val mockResult = mockk<com.mongodb.bulk.BulkWriteResult>()
        every { mockResult.insertedCount } returns 0
        every { mockResult.modifiedCount } returns 1
        every { mockResult.upserts } returns emptyList()

        every { mockBulkOps.replaceOne(any(), any<AppDailyActionSummary>(), any()) } returns
            mockBulkOps
        every { mockBulkOps.execute() } returns mockResult

        every { archiveService.saveAll(archived) } just runs

        // Act
        service.save(updated, archived)

        // Verify correct bulk operation
        verify(exactly = 1) { mongoTemplate.bulkOps(any(), AppDailyActionSummary::class.java) }
        verify(exactly = 1) { mockBulkOps.replaceOne(any(), updated.first(), any()) }
        verify(exactly = 1) { mockBulkOps.execute() }

        // Verify archive is saved
        verify(exactly = 1) { archiveService.saveAll(archived) }

        // Ensure repository was never used
        verify(exactly = 0) { repository.saveAll(any<List<AppDailyActionSummary>>()) }
    }

    @Test
    fun `save with empty lists does not call repositories`() {
        service.save(emptyList(), emptyList())

        verify(exactly = 0) { mongoTemplate.save(any<AppDailyActionSummary>()) }
        verify(exactly = 0) { archiveService.saveAll(any<List<AppDailyActionSummary>>()) }
        verify(exactly = 0) { repository.saveAll(any<List<AppDailyActionSummary>>()) }
    }

    @Test
    fun `createOrUpdateExisting with no existing record creates new one`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1757449050, // 2025-09-09
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
                blockTimestamp = 1757449050,
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

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                date = "2025-09-09",
                events = listOf(event1, event2),
                blockDetails = BlockDetails("block-1", 1L, 1757449050),
                existing = null,
            )

        assertEquals("app-1", result.appId)
        assertEquals("user-1", result.user)
        assertEquals(1, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(2, result.actionsRewarded)
        assertEquals("2025-09-09", result.date)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-2",
                blockNumber = 2L,
                blockTimestamp = 1757449060, // 2025-09-09
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
                blockTimestamp = 1757449060,
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
            AppDailyActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.TEN,
                totalImpact = null,
                date = "2025-09-09",
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                date = "2025-09-09",
                events = listOf(event1, event2),
                blockDetails = BlockDetails("block-2", 2L, 1757449060),
                existing = existing,
            )

        assertEquals("app-1", result.appId)
        assertEquals("user-1", result.user)
        assertEquals(2, result.version)
        assertEquals(2L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(40)))
        assertEquals(3, result.actionsRewarded)
        assertEquals("2025-09-09", result.date)
    }

    @Test
    fun `createOrUpdateExisting throws if events have different blockId, appId or receiverId`() {
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
                date = "2025-09-09",
                events = listOf(event1, event2),
                blockDetails = BlockDetails("block-1", 1L, 100L),
                existing = null,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                date = "2025-09-09",
                events = listOf(event1, event3),
                blockDetails = BlockDetails("block-1", 1L, 100L),
                existing = null,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                date = "2025-09-09",
                events = listOf(event1, event4),
                blockDetails = BlockDetails("block-1", 1L, 100L),
                existing = null,
            )
        }
    }

    @Test
    fun `createOrUpdateExisting sums impacts correctly`() {
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
                events = listOf(event1, event2),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
                date = "2025-09-09",
            )

        assertEquals(1, result.version)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        assertEquals("user-1", result.user)
        assertEquals("app-1", result.appId)
        assertEquals(2L, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(160, result.totalImpact?.carbon)
        assertEquals(350, result.totalImpact?.water)
        assertEquals(70, result.totalImpact?.energy)
    }

    @Test
    fun `resolveExisting returns from cache when present, otherwise repository`() {
        val cached =
            AppDailyActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
                date = "2025-09-09",
            )

        // Prefer cache
        val fromCache =
            service.callResolveExisting(
                "app-1:user-1:2025-09-09",
                mapOf("app-1:user-1:2025-09-09" to cached),
            )
        assertEquals(cached, fromCache)

        // Fallback to repository when not in cache
        every { repository.findByIdOrNull("app-1:user-2:2025-09-09") } returns
            cached.copy(user = "user-2")
        val fromRepo = service.callResolveExisting("app-1:user-2:2025-09-09", emptyMap())
        assertEquals("user-2", fromRepo?.user)
    }
}
