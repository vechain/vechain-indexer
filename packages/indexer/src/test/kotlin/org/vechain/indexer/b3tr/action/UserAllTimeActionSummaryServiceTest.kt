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
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class UserAllTimeActionSummaryServiceTest {
    @MockK lateinit var repository: UserAllTimeActionSummaryRepository

    @MockK
    lateinit var archiveService:
        ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>

    @MockK
    lateinit var pruner: PrunerService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: UserAllTimeActionSummaryRepository,
        archive: ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
        pruner: PrunerService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
    ) : UserAllTimeActionSummaryService(repository, archive, pruner) {
        fun callResolveExisting(recordId: String, cache: Map<String, UserAllTimeActionSummary>) =
            resolveExisting(recordId, cache)

        fun callCreateOrUpdateExisting(
            entity: String,
            entityType: EntityType,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            existing: UserAllTimeActionSummary?,
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
        assertEquals("user-2", updated[1].entity)
        assertEquals(EntityType.USER, updated[1].entityType)
        assertEquals(1, updated[1].actionsRewarded)
        assertEquals(0, updated[1].totalRewardAmount.compareTo(BigDecimal("20")))
        assertEquals("app-1", updated[2].entity)
        assertEquals(EntityType.APP, updated[2].entityType)
        assertEquals(2, updated[2].actionsRewarded)
        assertEquals(0, updated[2].totalRewardAmount.compareTo(BigDecimal("30")))
        assertEquals("GLOBAL", updated[3].entity)
        assertEquals(EntityType.GLOBAL, updated[3].entityType)
        assertEquals(2, updated[3].actionsRewarded)
        assertEquals(0, updated[3].totalRewardAmount.compareTo(BigDecimal("30")))

        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents updates if there is an existing record`() {
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

        val existingUserRecord =
            UserAllTimeActionSummary(
                id = generateId("user-1"),
                version = 1,
                blockId = "block-0",
                blockNumber = 0,
                blockTimestamp = 0,
                entity = "user-1",
                entityType = EntityType.USER,
                actionsRewarded = 5,
                totalRewardAmount = BigDecimal("50"),
                totalImpact = null,
            )
        val existingAppRecord =
            UserAllTimeActionSummary(
                id = generateId("app-1"),
                version = 2,
                blockId = "block-0",
                blockNumber = 0,
                blockTimestamp = 0,
                entity = "app-1",
                entityType = EntityType.APP,
                actionsRewarded = 10,
                totalRewardAmount = BigDecimal("100"),
                totalImpact = null,
            )
        val existingGlobalRecord =
            UserAllTimeActionSummary(
                id = generateId(EntityType.GLOBAL.name),
                version = 3,
                blockId = "block-0",
                blockNumber = 0,
                blockTimestamp = 0,
                entity = "GLOBAL",
                entityType = EntityType.GLOBAL,
                actionsRewarded = 20,
                totalRewardAmount = BigDecimal("200"),
                totalImpact = null,
            )

        every { repository.findByIdOrNull(generateId("user-1")) } returns existingUserRecord
        every { repository.findByIdOrNull(generateId("app-1")) } returns existingAppRecord
        every { repository.findByIdOrNull(generateId(EntityType.GLOBAL.name)) } returns
            existingGlobalRecord

        val (updated, archived) = service.processEvents(listOf(event))

        assertEquals(3, updated.size)
        val updatedUserRecord =
            updated.first { it.entity == "user-1" && it.entityType == EntityType.USER }
        assertEquals(6, updatedUserRecord.actionsRewarded)
        assertEquals(0, updatedUserRecord.totalRewardAmount.compareTo(BigDecimal("60")))
        assertEquals(2, updatedUserRecord.version)
        val updatedAppRecord =
            updated.first { it.entity == "app-1" && it.entityType == EntityType.APP }
        assertEquals(11, updatedAppRecord.actionsRewarded)
        assertEquals(0, updatedAppRecord.totalRewardAmount.compareTo(BigDecimal("110")))
        assertEquals(3, updatedAppRecord.version)
        val updatedGlobalRecord =
            updated.first { it.entity == "GLOBAL" && it.entityType == EntityType.GLOBAL }
        assertEquals(21, updatedGlobalRecord.actionsRewarded)
        assertEquals(0, updatedGlobalRecord.totalRewardAmount.compareTo(BigDecimal("210")))
        assertEquals(4, updatedGlobalRecord.version)
        assertEquals(3, archived.size)
        assertEquals(
            existingUserRecord,
            archived.first { it.entity == "user-1" && it.entityType == EntityType.USER },
        )
        assertEquals(
            existingAppRecord,
            archived.first { it.entity == "app-1" && it.entityType == EntityType.APP },
        )
        assertEquals(
            existingGlobalRecord,
            archived.first { it.entity == "GLOBAL" && it.entityType == EntityType.GLOBAL },
        )
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
                UserAllTimeActionSummary(
                    version = 2,
                    blockId = "b2",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    entity = "user-1",
                    entityType = EntityType.USER,
                    actionsRewarded = 3,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                )
            )
        val archived =
            listOf(
                UserAllTimeActionSummary(
                    version = 1,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    entity = "user-1",
                    entityType = EntityType.USER,
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
        verify(exactly = 0) { repository.saveAll(any<List<UserAllTimeActionSummary>>()) }
        verify(exactly = 0) { archiveService.saveAll(any<List<UserAllTimeActionSummary>>()) }
    }

    @Test
    fun `createOrUpdateExisting with no existing record creates new one`() {
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
        val blockDetails = BlockDetails("block-1", 1L, 100L)

        val result =
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event),
                blockDetails = blockDetails,
                existing = null,
            )

        assertEquals(1, result.version)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(1, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal("10")))
        assertEquals(null, result.totalImpact)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
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
        val blockDetails = BlockDetails("block-1", 1L, 100L)
        val existing =
            UserAllTimeActionSummary(
                id = generateId("user-1"),
                version = 5,
                blockId = "block-0",
                blockNumber = 0,
                blockTimestamp = 0,
                entity = "user-1",
                entityType = EntityType.USER,
                actionsRewarded = 10,
                totalRewardAmount = BigDecimal("100"),
                totalImpact = null,
            )

        val result =
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event),
                blockDetails = blockDetails,
                existing = existing,
            )

        assertEquals(6, result.version)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(11, result.actionsRewarded)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal("110")))
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
                blockId = "block-2",
                blockNumber = 2L,
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
        val blockDetails = BlockDetails("block-1", 1L, 100L)

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
            UserAllTimeActionSummary(
                version = 1,
                blockId = "b1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                entity = "user-1",
                entityType = EntityType.USER,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal.ONE,
                totalImpact = null,
            )

        // Prefer cache
        val fromCache = service.callResolveExisting("app-1:user-1", mapOf("app-1:user-1" to cached))
        assertEquals(cached, fromCache)

        // Fallback to repository when not in cache
        every { repository.findByIdOrNull("app-1:user-2") } returns cached.copy(entity = "user-2")
        val fromRepo = service.callResolveExisting("app-1:user-2", emptyMap())
        assertEquals("user-2", fromRepo?.entity)
    }
}
