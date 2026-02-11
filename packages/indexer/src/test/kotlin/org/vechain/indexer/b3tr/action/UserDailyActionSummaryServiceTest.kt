package org.vechain.indexer.b3tr.action

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class UserDailyActionSummaryServiceTest {
    @MockK lateinit var repository: UserDailyActionSummaryRepository

    @MockK lateinit var archiveService: ArchiveService<UserDailyActionSummary>

    @MockK lateinit var pruner: TargetedPruner<UserDailyActionSummary>

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: UserDailyActionSummaryRepository,
        archive: ArchiveService<UserDailyActionSummary>,
        pruner: TargetedPruner<UserDailyActionSummary>,
        impactConfig: ActionImpactConfig = ActionImpactConfig(),
    ) : UserDailyActionSummaryService(repository, archive, pruner, impactConfig) {
        fun callResolveExisting(recordId: String, cache: Map<String, UserDailyActionSummary>) =
            resolveExisting(recordId, cache)

        fun callCreateOrUpdateExisting(
            entity: String,
            entityType: EntityType,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            existing: UserDailyActionSummary?,
        ) = createOrUpdateExisting(entity, entityType, events, blockDetails, existing)
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
    fun `processEvents creates records for all entities (users, apps and global)`() {
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
                blockNumber = 2L,
                blockTimestamp = 1757449050, // 2025-09-09
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

        every { repository.findByIdOrNull(any()) } returns null
        val (updated, archived) = service.processEvents(listOf(event1, event2))

        assertEquals(4, updated.size)
        assertEquals("user-1", updated[0].entity)
        assertEquals(EntityType.USER, updated[0].entityType)
        assertEquals(1, updated[0].actionsRewarded)
        assertEquals(0, updated[0].totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals("2025-09-09", updated[0].date)
        assertEquals("user-2", updated[1].entity)
        assertEquals(EntityType.USER, updated[1].entityType)
        assertEquals(1, updated[1].actionsRewarded)
        assertEquals(0, updated[1].totalRewardAmount.compareTo(BigDecimal("20")))
        assertEquals("2025-09-09", updated[1].date)
        assertEquals("app-1", updated[2].entity)
        assertEquals(EntityType.APP, updated[2].entityType)
        assertEquals(2, updated[2].actionsRewarded)
        assertEquals(0, updated[2].totalRewardAmount.compareTo(BigDecimal("30")))
        assertEquals("2025-09-09", updated[2].date)
        assertEquals("GLOBAL", updated[3].entity)
        assertEquals(EntityType.GLOBAL, updated[3].entityType)
        assertEquals(2, updated[3].actionsRewarded)
        assertEquals(0, updated[3].totalRewardAmount.compareTo(BigDecimal("30")))
        assertEquals("2025-09-09", updated[3].date)

        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents updates if there is an existing record`() {
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
                blockNumber = 2L,
                blockTimestamp = 1757449050, // 2025-09-09
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

        val existingUser1 =
            UserDailyActionSummary(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 1757362650, // 2025-09-08
                entity = "user-1",
                entityType = EntityType.USER,
                date = "2025-09-09",
                actionsRewarded = 4,
                totalRewardAmount = BigDecimal("40"),
                totalImpact = null,
            )
        val existingApp1 =
            UserDailyActionSummary(
                version = 2,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 1757362650, // 2025-09-08
                entityType = EntityType.APP,
                entity = "app-1",
                date = "2025-09-09",
                actionsRewarded = 5,
                totalRewardAmount = BigDecimal("50"),
                totalImpact = null,
            )
        val existingGlobal =
            UserDailyActionSummary(
                version = 3,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 1757362650, // 2025-09-08
                entityType = EntityType.GLOBAL,
                entity = "GLOBAL",
                date = "2025-09-09",
                actionsRewarded = 6,
                totalRewardAmount = BigDecimal("60"),
                totalImpact = null,
            )

        every { repository.findByIdOrNull(generateId("user-1", "2025-09-09")) } returns
            existingUser1
        every { repository.findByIdOrNull(generateId("user-2", "2025-09-09")) } returns null
        every { repository.findByIdOrNull(generateId("app-1", "2025-09-09")) } returns existingApp1
        every {
            repository.findByIdOrNull(generateId(EntityType.GLOBAL.name, "2025-09-09"))
        } returns existingGlobal

        val (updated, archived) = service.processEvents(listOf(event1, event2))
        assertEquals(4, updated.size)
        assertEquals("user-1", updated[0].entity)
        assertEquals(EntityType.USER, updated[0].entityType)
        assertEquals(5, updated[0].actionsRewarded)
        assertEquals(0, updated[0].totalRewardAmount.compareTo(BigDecimal("50")))
        assertEquals("2025-09-09", updated[0].date)
        assertEquals("user-2", updated[1].entity)
        assertEquals(EntityType.USER, updated[1].entityType)
        assertEquals(1, updated[1].actionsRewarded)
        assertEquals(0, updated[1].totalRewardAmount.compareTo(BigDecimal("20")))
        assertEquals("2025-09-09", updated[1].date)
        assertEquals("app-1", updated[2].entity)
        assertEquals(EntityType.APP, updated[2].entityType)
        assertEquals(7, updated[2].actionsRewarded)
        assertEquals(0, updated[2].totalRewardAmount.compareTo(BigDecimal("80")))
        assertEquals("2025-09-09", updated[2].date)
        assertEquals("GLOBAL", updated[3].entity)
        assertEquals(EntityType.GLOBAL, updated[3].entityType)
        assertEquals(8, updated[3].actionsRewarded)
        assertEquals(0, updated[3].totalRewardAmount.compareTo(BigDecimal("90")))
        assertEquals("2025-09-09", updated[3].date)

        assertEquals(3, archived.size)
        assertEquals(existingUser1, archived[0])
        assertEquals(existingApp1, archived[1])
        assertEquals(existingGlobal, archived[2])
    }

    @Test
    fun `save with empty lists does not call repositories`() {
        service.save(emptyList(), emptyList())
        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { archiveService.saveAll(any()) }
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

        val blockDetails = BlockDetails("block-1", 1L, 1757449050)

        val result =
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event1),
                blockDetails = blockDetails,
                existing = null,
            )
        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(1, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal("10")))
        assertEquals("2025-09-09", result.date)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(1757449050, result.blockTimestamp)
        assertEquals(1, result.version)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
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

        val blockDetails = BlockDetails("block-1", 1L, 1757449050)
        val existing =
            UserDailyActionSummary(
                version = 2,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 1757362650, // 2025-09-08
                entity = "user-1",
                entityType = EntityType.USER,
                date = "2025-09-09",
                actionsRewarded = 4,
                totalRewardAmount = BigDecimal("40"),
                totalImpact = null,
            )

        val result =
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event1),
                blockDetails = blockDetails,
                existing = existing,
            )
        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(5, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal("50")))
        assertEquals("2025-09-09", result.date)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(1757449050, result.blockTimestamp)
        assertEquals(3, result.version)
    }

    @Test
    fun `createOrUpdateExisting throws if events have different blockId, appId or receiverId`() {
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
                blockId = "block-2", // Different blockId
                blockNumber = 2L,
                blockTimestamp = 1757449050, // 2025-09-09
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
                blockNumber = 3L,
                blockTimestamp = 1757449050, // 2025-09-09
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
                blockNumber = 4L,
                blockTimestamp = 1757449050, // 2025-09-09
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-1",
                                "receiver" to "user-2", // Different receiverId
                                "amount" to "20000000000000000000",
                                "action" to "",
                                "distributor" to "0x0",
                            )
                    ),
            )

        val blockDetails = BlockDetails("block-1", 1L, 1757449050)

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event1, event2),
                blockDetails = blockDetails,
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
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event1, event2),
                blockDetails =
                    BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 100L),
                existing = null,
            )

        assertEquals(1, result.version)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(2L, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(160, result.totalImpact?.carbon)
        assertEquals(350, result.totalImpact?.water)
        assertEquals(70, result.totalImpact?.energy)
    }

    @Test
    fun `resolveExisting returns from cache when present, otherwise repository`() {
        val cached =
            UserDailyActionSummary(
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                entity = "user-1",
                entityType = EntityType.USER,
                date = "2025-09-09",
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )
        val fromRepo =
            UserDailyActionSummary(
                version = 2,
                blockId = "block-2",
                blockNumber = 2L,
                blockTimestamp = 200L,
                entity = "user-2",
                entityType = EntityType.USER,
                date = "2025-09-09",
                actionsRewarded = 2,
                totalRewardAmount = BigDecimal(20),
                totalImpact = null,
            )

        val cache = mapOf("id-1" to cached)
        every { repository.findByIdOrNull("id-2") } returns fromRepo
        every { repository.findByIdOrNull("id-3") } returns null
        assertEquals(cached, service.callResolveExisting("id-1", cache))
        assertEquals(fromRepo, service.callResolveExisting("id-2", cache))
        assertEquals(null, service.callResolveExisting("id-3", cache))
    }
}
