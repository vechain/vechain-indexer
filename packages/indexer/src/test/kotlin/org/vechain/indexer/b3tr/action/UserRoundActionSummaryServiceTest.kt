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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId

@ExtendWith(MockKExtension::class)
internal class UserRoundActionSummaryServiceTest {
    @MockK lateinit var repository: UserRoundActionSummaryRepository

    @MockK
    lateinit var archiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>

    @MockK
    lateinit var pruner: TargetedPruner<UserRoundActionSummary, UserRoundActionSummaryArchive>

    private lateinit var service: TestableService

    // A small testable subclass to expose protected methods where useful
    private class TestableService(
        repository: UserRoundActionSummaryRepository,
        archive: ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
        pruner: TargetedPruner<UserRoundActionSummary, UserRoundActionSummaryArchive>,
        impactConfig: ActionImpactConfig = ActionImpactConfig(),
    ) : UserRoundActionSummaryService(repository, archive, pruner, impactConfig) {
        fun callCreateOrUpdateExisting(
            entity: String,
            entityType: EntityType,
            events: List<IndexedEvent>,
            blockDetails: BlockDetails,
            roundId: Int,
            existing: UserRoundActionSummary?,
            version: Int,
        ) =
            createOrUpdateExisting(
                entity,
                entityType,
                events,
                blockDetails,
                roundId,
                existing,
                version,
            )
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, archiveService, pruner)
    }

    @Test
    fun `processEvents no existing record results in new record being created`() {
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

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event), roundId = 1)

        assertEquals(3, updated.size)
        assertEquals("user-1", updated.first().entity)
        assertEquals(1, updated.first().roundId)
        assertEquals(EntityType.USER, updated.first().entityType)
        assertEquals(1, updated.first().version)
        assertEquals(1L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(1, updated.first().roundId)
        assertEquals(1, updated.first().actionsRewarded)
        assertEquals("app-1", updated[1].entity)
        assertEquals(EntityType.APP, updated[1].entityType)
        assertEquals(1, updated[1].roundId)
        assertEquals(EntityType.GLOBAL.name, updated[2].entity)
        assertEquals(EntityType.GLOBAL, updated[2].entityType)
        assertEquals(1, updated[2].roundId)

        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents multiple events with same appId and user result in a single new record`() {
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

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2), roundId = 1)

        assertEquals(3, updated.size)
        assertEquals("user-1", updated.first().entity)
        assertEquals(1, updated.first().roundId)
        assertEquals(EntityType.USER, updated.first().entityType)
        assertEquals(1, updated.first().version)
        assertEquals(1L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(2, updated.first().actionsRewarded)
        assertEquals("app-1", updated[1].entity)
        assertEquals(EntityType.APP, updated[1].entityType)
        assertEquals(1, updated[1].roundId)
        assertEquals(EntityType.GLOBAL.name, updated[2].entity)
        assertEquals(EntityType.GLOBAL, updated[2].entityType)
        assertEquals(1, updated[2].roundId)

        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents existing record results in record being updated`() {
        val blockDetails = BlockDetails("block-1", 2L, 2000L)

        val event =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
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

        val existingRecord =
            UserRoundActionSummary(
                id = generateId("user-1", "1"),
                version = 1,
                blockId = "block-0",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                entityType = EntityType.USER,
                entity = "user-1",
                roundId = 1,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        every { repository.findByIdOrNull(existingRecord.id) } returns existingRecord
        every { repository.findByIdOrNull(generateId("app-1", "1")) } returns null
        every { repository.findByIdOrNull(generateId(EntityType.GLOBAL.name, "1")) } returns null

        val (updated, archived) = service.processEvents(listOf(event), roundId = 1)

        assertEquals(3, updated.size)
        assertEquals("user-1", updated.first().entity)
        assertEquals(1, updated.first().roundId)
        assertEquals(EntityType.USER, updated.first().entityType)
        assertEquals(2, updated.first().version)
        assertEquals(2L, updated.first().blockNumber)
        assertEquals(0, updated.first().totalRewardAmount.compareTo(BigDecimal(20)))
        assertEquals(2, updated.first().actionsRewarded)
        assertEquals("app-1", updated[1].entity)
        assertEquals(EntityType.APP, updated[1].entityType)
        assertEquals(1, updated[1].roundId)
        assertEquals(EntityType.GLOBAL.name, updated[2].entity)
        assertEquals(EntityType.GLOBAL, updated[2].entityType)
        assertEquals(1, updated[2].roundId)

        assertEquals(1, archived.size)
        assertEquals(existingRecord, archived.first())
    }

    @Test
    fun `processEvents throws if all events are not of type B3TR_ActionReward`() {
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
    fun `save applies updates and archives when lists are non empty`() {
        val updated =
            listOf(
                UserRoundActionSummary(
                    version = 2,
                    blockId = "b2",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    entity = "user-1",
                    entityType = EntityType.USER,
                    actionsRewarded = 3,
                    totalRewardAmount = BigDecimal.TEN,
                    totalImpact = null,
                    roundId = 1,
                )
            )
        val archived =
            listOf(
                UserRoundActionSummary(
                    version = 1,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    entity = "user-1",
                    entityType = EntityType.USER,
                    actionsRewarded = 1,
                    totalRewardAmount = BigDecimal.ONE,
                    totalImpact = null,
                    roundId = 1,
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
        verify(exactly = 0) { repository.saveAll(any<List<UserRoundActionSummary>>()) }
        verify(exactly = 0) { archiveService.saveAll(any<List<UserRoundActionSummary>>()) }
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
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event),
                blockDetails = blockDetails,
                roundId = 1,
                existing = null,
                version = 1,
            )

        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(1, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(10)))
        assertEquals(1, result.actionsRewarded)
        assertEquals(1, result.roundId)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
        val blockDetails = BlockDetails("block-1", 2L, 2000L)

        val event =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
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

        val existingRecord =
            UserRoundActionSummary(
                id = generateId("user-1", "1"),
                version = 1,
                blockId = "block-0",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                entityType = EntityType.USER,
                entity = "user-1",
                roundId = 1,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val result =
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event),
                blockDetails = blockDetails,
                roundId = 1,
                existing = existingRecord,
                version = existingRecord.version + 1,
            )

        assertEquals("user-1", result.entity)
        assertEquals(EntityType.USER, result.entityType)
        assertEquals(2, result.version)
        assertEquals(2L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(20)))
        assertEquals(2, result.actionsRewarded)
        assertEquals(1, result.roundId)
    }

    @Test
    fun `createOrUpdateExisting throws if events have different blockId or entity`() {
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
                blockNumber = 1L,
                eventType = "B3TR_ActionReward",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "appId" to "app-2",
                                "receiver" to "user-2",
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

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.USER,
                events = listOf(event1, event2),
                blockDetails = blockDetails,
                roundId = 1,
                existing = null,
                version = 1,
            )
        }

        assertThrows<IllegalArgumentException> {
            service.callCreateOrUpdateExisting(
                entity = "user-1",
                entityType = EntityType.APP,
                events = listOf(event1, event3),
                blockDetails = blockDetails,
                roundId = 1,
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
                entity = "app-1",
                entityType = EntityType.APP,
                roundId = 1,
                events = listOf(event1, event2),
                blockDetails = blockDetails,
                existing = null,
                version = 1,
            )

        assertEquals("app-1", result.entity)
        assertEquals(1, result.version)
        assertEquals(1L, result.blockNumber)
        assertEquals(0, result.totalRewardAmount.compareTo(BigDecimal(30)))
        assertEquals(1, result.roundId)
        assertEquals(160, result.totalImpact?.carbon)
        assertEquals(350, result.totalImpact?.water)
        assertEquals(70, result.totalImpact?.energy)
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

        val expectedUserRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = "user-1",
                entityType = EntityType.USER,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val expectedAppRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = "app-1",
                entityType = EntityType.APP,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val expectedGlobalRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = EntityType.GLOBAL.name,
                entityType = EntityType.GLOBAL,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val updatedRecordsExpected =
            listOf(expectedUserRecord, expectedAppRecord, expectedGlobalRecord)

        every { repository.findByIdOrNull(generateId(expectedUserRecord.entity, "2")) } returns null
        every { repository.findByIdOrNull(generateId(expectedAppRecord.entity, "2")) } returns null
        every { repository.findByIdOrNull(generateId(expectedGlobalRecord.entity, "2")) } returns
            null

        // Verify that service.save is called with the correct parameters
        val (updated, archive, updatedRoundId) = service.processEvents(events, 1)

        verify(exactly = 1) {
            repository.findByIdOrNull(generateId(expectedUserRecord.entity, "2"))
        }
        verify(exactly = 1) { repository.findByIdOrNull(generateId(expectedAppRecord.entity, "2")) }
        verify(exactly = 1) {
            repository.findByIdOrNull(generateId(expectedGlobalRecord.entity, "2"))
        }

        assertTrue(
            updated.zip(updatedRecordsExpected).all { (actual, expected) ->
                actual.copy(totalRewardAmount = BigDecimal.ZERO) ==
                    expected.copy(totalRewardAmount = BigDecimal.ZERO) &&
                    actual.totalRewardAmount.compareTo(expected.totalRewardAmount) == 0
            }
        )
        assertEquals(emptyList<UserRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
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

        val expectedUserRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = "user-1",
                entityType = EntityType.USER,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val expectedAppRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = "app-1",
                entityType = EntityType.APP,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val expectedGlobalRecord =
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetailsEvent1.blockId,
                blockNumber = blockDetailsEvent1.blockNumber,
                blockTimestamp = blockDetailsEvent1.blockTimestamp,
                entity = EntityType.GLOBAL.name,
                entityType = EntityType.GLOBAL,
                roundId = 2,
                actionsRewarded = 1,
                totalRewardAmount = BigDecimal(10),
                totalImpact = null,
            )

        val updatedRecordsExpected =
            listOf(expectedUserRecord, expectedAppRecord, expectedGlobalRecord)

        every { repository.findByIdOrNull(generateId(expectedUserRecord.entity, "2")) } returns null
        every { repository.findByIdOrNull(generateId(expectedAppRecord.entity, "2")) } returns null
        every { repository.findByIdOrNull(generateId(expectedGlobalRecord.entity, "2")) } returns
            null

        // Verify that service.save is called with the correct parameters
        val (updated, archive, updatedRoundId) = service.processEvents(events, 1)

        verify(exactly = 1) {
            repository.findByIdOrNull(generateId(expectedUserRecord.entity, "2"))
        }
        verify(exactly = 1) { repository.findByIdOrNull(generateId(expectedAppRecord.entity, "2")) }
        verify(exactly = 1) {
            repository.findByIdOrNull(generateId(expectedGlobalRecord.entity, "2"))
        }

        assertTrue(
            updated.zip(updatedRecordsExpected).all { (actual, expected) ->
                actual.copy(totalRewardAmount = BigDecimal.ZERO) ==
                    expected.copy(totalRewardAmount = BigDecimal.ZERO) &&
                    actual.totalRewardAmount.compareTo(expected.totalRewardAmount) == 0
            }
        )
        assertEquals(emptyList<UserRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
    }

    @Test
    fun `only round change event should result in a roundId change`() {
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

        val (updated, archive, updatedRoundId) = service.processEvents(events, 1)

        assertEquals(emptyList<UserRoundActionSummary>(), updated)
        assertEquals(emptyList<UserRoundActionSummary>(), archive)
        assertEquals(2, updatedRoundId)
    }
}
