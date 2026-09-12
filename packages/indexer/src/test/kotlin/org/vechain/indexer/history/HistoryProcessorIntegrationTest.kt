package org.vechain.indexer.history

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.validator.ValidatorDelegationService

/** The processor against a real schema: fixture blocks in, rows, lifecycle state and resume out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: HistoryWriteRepository
    private lateinit var lifecycle: DelegationLifecycleHistoryService
    private lateinit var processor: HistoryProcessor

    @BeforeAll
    fun start() {
        database.start()
        repository = HistoryWriteRepository(database.jdbc)
        val delegation = mockk<ValidatorDelegationService>()
        every { delegation.nextStatus(org.vechain.indexer.validator.Status.QUEUED) } returns
            org.vechain.indexer.validator.Status.ACTIVE
        coEvery { delegation.resolveCycleInfo(any(), any(), any()) } answers
            {
                5L to (secondArg<Long>() + 5L)
            }
        lifecycle =
            DelegationLifecycleHistoryService(
                repository = repository,
                validatorDelegationService = delegation,
                stakerSC = "0x00000000000000000000000000005374616B6572",
                stargateNftContract = "0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7",
            )
        val service =
            HistoryService(
                repository = repository,
                delegationLifecycleHistoryService = lifecycle,
                validatorRepository = mockk { every { findAll() } returns emptyList() },
                validatorStartBlock = Long.MAX_VALUE,
            )
        processor =
            HistoryProcessor(
                service,
                repository,
                IndexerStateRepository(database.jdbc),
                CheckpointProperties().apply { saveIntervalSeconds = 0 },
                InlineVersioningProperties(),
                ProcessorMetrics(SimpleMeterRegistry()),
                version = 1,
            )
        processor.bootstrap()
    }

    @AfterAll fun stop() = database.close()

    private fun process(
        block: Block,
        events: List<org.vechain.indexer.event.model.generic.IndexedEvent>,
    ) = runBlocking {
        processor.process(IndexingResult.BlockResult(block, events, emptyList(), Status.SYNCING))
    }

    @Test
    fun `blocks are written, resumed from, replayed and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val early = BlockFixtures.BLOCK_MULTIPLE_TXS // block 8, ten transactions, no decoded events
        val late = BlockFixtures.BLOCK_TRANSFERS // block 22223545
        val both = "block_number IN (${early.number}, ${late.number})"

        process(early, emptyList())
        process(late, IndexedEventsFixtures.INDEXED_EVENTS_TRANSFERS)

        val unknown = "event_name = 'UNKNOWN_TX' AND block_number = ${early.number}"
        assertEquals(early.transactions.size, database.count("history.event WHERE $unknown"))
        val lateRows = database.count("history.event WHERE block_number = ${late.number}")
        val addresses = database.count("history.event_address WHERE $both")
        assertEquals(BlockIdentifier(late.number, late.id), processor.getLastSyncedBlock())

        process(late, IndexedEventsFixtures.INDEXED_EVENTS_TRANSFERS)
        assertEquals(addresses, database.count("history.event_address WHERE $both"))
        assertEquals(
            early.transactions.size + lateRows,
            database.count("history.event WHERE $both"),
        )

        processor.rollback(late.number)
        assertEquals(early.transactions.size, database.count("history.event WHERE $both"))
        assertEquals(0, database.count("history.event_address WHERE block_number = ${late.number}"))
        assertEquals(BlockIdentifier(late.number - 1, null), processor.getLastSyncedBlock())
    }

    @Test
    fun `lifecycle state is rebuilt from the newest row per delegation`() {
        val request =
            IndexedHistoryEvent(
                id = "a".repeat(40),
                blockId = "0x" + "1".repeat(64),
                blockNumber = 1,
                blockTimestamp = 10,
                txId = "0x" + "2".repeat(64),
                eventName = HistoryEventName.STARGATE_DELEGATE_REQUEST,
                tokenId = "7",
                delegationId = "9",
                validator = "0x" + "3".repeat(40),
                owner = "0x" + "4".repeat(40),
                origin = "0x" + "4".repeat(40),
                delegationLifecycleStatus = org.vechain.indexer.validator.Status.QUEUED,
                delegationLifecycleNextCycle = 20,
                delegationLifecycleCycleLength = 5,
                delegationLifecycleForceExit = false,
                delegationLifecycleOrder = 1000,
            )
        repository.save(
            listOf(
                request,
                request.copy(
                    id = "b".repeat(40),
                    blockNumber = 0,
                    delegationLifecycleNextCycle = 15,
                ),
            )
        )
        lifecycle.invalidate()

        val activated = runBlocking { lifecycle.onBlockStart(block(20), emptyMap()) }

        assertEquals(
            listOf(HistoryEventName.STARGATE_DELEGATE_ACTIVE),
            activated.map { it.eventName },
        )
        assertEquals("9", activated.single().delegationId)
        assertEquals(request.owner, activated.single().owner)
    }

    private fun block(number: Long) =
        Block(
            id = "0x" + number.toString(16).padStart(64, '0'),
            number = number,
            timestamp = number * 10,
            parentID = "0x" + "0".repeat(64),
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x0",
            beneficiary = "0x" + "0".repeat(40),
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "0x" + "0".repeat(64),
            txsFeatures = 0,
            stateRoot = "0x" + "0".repeat(64),
            receiptsRoot = "0x" + "0".repeat(64),
            com = false,
            signer = "0x" + "0".repeat(40),
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
        )
}
