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
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class AppAllTimeActionSummaryServiceTest {
    @MockK lateinit var repository: AppAllTimeActionSummaryRepository

    @MockK lateinit var archiveService: ArchiveService<AppAllTimeActionSummary>

    @MockK lateinit var pruner: TargetedPruner<AppAllTimeActionSummary>

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: AppAllTimeActionSummaryRepository,
        archive: ArchiveService<AppAllTimeActionSummary>,
        pruner: TargetedPruner<AppAllTimeActionSummary>,
        impactConfig: ActionImpactConfig = ActionImpactConfig(),
    ) : AppAllTimeActionSummaryService(repository, archive, pruner, impactConfig) {
        fun callResolveExisting(recordId: String, cache: Map<String, AppAllTimeActionSummary>) =
            resolveExisting(recordId, cache)

        fun callCreateOrUpdateExisting(
            appId: String,
            receiverId: String,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            existing: AppAllTimeActionSummary?,
        ) = createOrUpdateExisting(appId, receiverId, events, blockDetails, existing)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, archiveService, pruner)
    }

    @Test
    fun `processEvents with empty events returns empty lists`() {
        val (updated, archived) = service.processEvents(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents same appId and receiver but different block results in record update`() {
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

        every { repository.findByIdOrNull(generateId("app-1", "user-1")) } returns null
        val (updated, archived) = service.processEvents(listOf(event1, event2))

        assertEquals(1, updated.size)
        assertEquals("app-1", updated.first().appId)
        assertEquals("user-1", updated.first().user)
        assertEquals(2, updated.first().version)
        assertEquals(2L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(30)))

        assertEquals(1, archived.size)
        assertEquals(1, archived.first().version)
        assertEquals("app-1", archived.first().appId)
        assertEquals(1L, archived.first().blockNumber)
        assertEquals(0, archived.first().totalRewardAmount.compareTo(BigDecimal(10)))
    }

    @Test
    fun `processEvents updates if there is an existing record`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-2",
                blockNumber = 2L,
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

        every { repository.findByIdOrNull(generateId("app-1", "user-1")) } returns
            AppAllTimeActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
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
                AppAllTimeActionSummary(
                    version = 2,
                    blockId = "b2",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 3,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                )
            )
        val archived =
            listOf(
                AppAllTimeActionSummary(
                    version = 1,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    user = "user-1",
                    appId = "app-1",
                    actionsRewarded = 1,
                    totalRewardAmount = BigDecimal.ONE,
                    totalImpact = null,
                )
            )

        every { repository.saveAll(updated) } returns updated
        every { archiveService.saveAll(archived) } just runs

        service.save(updated, archived)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { archiveService.saveAll(archived) }
    }

    @Test
    fun `save with empty lists does not call repositories`() {
        service.save(emptyList(), emptyList())
        verify(exactly = 0) { repository.saveAll(any<List<AppAllTimeActionSummary>>()) }
        verify(exactly = 0) { archiveService.saveAll(any<List<AppAllTimeActionSummary>>()) }
    }

    @Test
    fun `createOrUpdateExisting with no existing record creates new one`() {
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

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(event1, event2),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )

        assertEquals(1, result.version)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        assertEquals("user-1", result.user)
        assertEquals("app-1", result.appId)
        assertEquals(2L, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(null, result.totalImpact)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-2",
                blockNumber = 2L,
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

        val existing =
            AppAllTimeActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(event1),
                blockDetails =
                    BlockDetails(blockId = "block-2", blockNumber = 2L, blockTimestamp = 200L),
                existing = existing,
            )

        assertEquals(2, result.version)
        assertEquals("block-2", result.blockId)
        assertEquals(2L, result.blockNumber)
        assertEquals(200L, result.blockTimestamp)
        assertEquals("user-1", result.user)
        assertEquals("app-1", result.appId)
        assertEquals(2L, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(11)))
        assertEquals(null, result.totalImpact)
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
                blockId = "block-2", // Different blockId
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
                                "appId" to "app-2", // Different appId
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
                                "receiver" to "user-2", // Different receiver
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
                events = listOf(event1, event2),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(event1, event3),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(event1, event4),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
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
            AppAllTimeActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
            )

        // Prefer cache
        val fromCache = service.callResolveExisting("app-1:user-1", mapOf("app-1:user-1" to cached))
        assertEquals(cached, fromCache)

        // Fallback to repository when not in cache
        every { repository.findByIdOrNull("app-1:user-2") } returns cached.copy(user = "user-2")
        val fromRepo = service.callResolveExisting("app-1:user-2", emptyMap())
        assertEquals("user-2", fromRepo?.user)
    }

    @Test
    fun `createOrUpdateExisting filters out impacts that exceed threshold`() {
        // Create events with both valid and invalid impacts
        val validEvent =
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
                                    "{\"version\": 2,\"description\": \"normal impact\",\"impact\": {\"carbon\": 100,\"water\": 500}}",
                            )
                    ),
            )

        // This impact exceeds the default threshold of 1,000,000
        val invalidEvent =
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
                                    "{\"version\": 2,\"description\": \"excessive impact\",\"impact\": {\"carbon\": 2000000,\"water\": 100}}",
                            )
                    ),
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(validEvent, invalidEvent),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )

        // Should only include the valid impact (carbon: 100, water: 500)
        // The invalid impact (carbon: 2000000, water: 100) should be filtered out
        assertEquals(100, result.totalImpact?.carbon)
        assertEquals(500, result.totalImpact?.water)
        assertEquals(null, result.totalImpact?.energy)
    }

    @Test
    fun `createOrUpdateExisting with all impacts exceeding threshold results in null totalImpact`() {
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
                                    "{\"version\": 2,\"description\": \"excessive impact 1\",\"impact\": {\"carbon\": 5000000}}",
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
                                    "{\"version\": 2,\"description\": \"excessive impact 2\",\"impact\": {\"water\": 10000000}}",
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
            )

        // All impacts exceeded threshold, so totalImpact should be null
        assertEquals(null, result.totalImpact)
        // But actions should still be rewarded and amount counted
        assertEquals(2L, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
    }

    @Test
    fun `createOrUpdateExisting with custom threshold filters appropriately`() {
        // Create service with lower threshold for carbon
        val customService =
            TestableService(
                repository,
                archiveService,
                pruner,
                ActionImpactConfig().apply { carbon = 1000 },
            )

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
                                "proof" to
                                    "{\"version\": 2,\"description\": \"moderate impact\",\"impact\": {\"carbon\": 1500}}",
                            )
                    ),
            )

        val result =
            customService.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(event),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )

        // Impact of 1500 exceeds threshold of 1000, so should be filtered
        assertEquals(null, result.totalImpact)
    }

    @Test
    fun `createOrUpdateExisting accumulates existing impact with new valid impacts only`() {
        val existing =
            AppAllTimeActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                user = "user-1",
                appId = "app-1",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = Impact(carbon = 500, water = 300),
            )

        val validEvent =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-2",
                blockNumber = 2L,
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
                                    "{\"version\": 2,\"description\": \"valid\",\"impact\": {\"carbon\": 100,\"energy\": 50}}",
                            )
                    ),
            )

        val invalidEvent =
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
                                "proof" to
                                    "{\"version\": 2,\"description\": \"invalid\",\"impact\": {\"water\": 5000000}}",
                            )
                    ),
            )

        val result =
            service.callCreateOrUpdateExisting(
                appId = "app-1",
                receiverId = "user-1",
                events = listOf(validEvent, invalidEvent),
                blockDetails =
                    BlockDetails(blockId = "block-2", blockNumber = 2L, blockTimestamp = 200L),
                existing = existing,
            )

        // Should accumulate existing (500, 300, null) with valid new (100, null, 50)
        // Invalid impact (null, 5000000, null) should be filtered out
        assertEquals(600, result.totalImpact?.carbon) // 500 + 100
        assertEquals(300, result.totalImpact?.water) // 300 + 0 (invalid was filtered)
        assertEquals(50, result.totalImpact?.energy) // 0 + 50
    }
}
