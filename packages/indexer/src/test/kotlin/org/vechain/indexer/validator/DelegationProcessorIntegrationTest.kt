package org.vechain.indexer.validator

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
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
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor on a real schema, the service mocked: states in, current rows and resume out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegationProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: DelegationWriteRepository
    private val service = mockk<DelegationService>()
    private lateinit var processor: DelegationProcessor

    @BeforeAll
    fun start() {
        database.start()
        writer = DelegationWriteRepository(database.jdbc)
        every { service.save(any()) } answers { writer.save(firstArg()) }
        every { service.invalidateCache() } returns Unit
        processor =
            DelegationProcessor(
                service,
                writer,
                IndexerStateRepository(database.jdbc),
                CheckpointProperties().apply { saveIntervalSeconds = 0 },
                InlineVersioningProperties(),
                ProcessorMetrics(SimpleMeterRegistry()),
                version = 1,
            )
        processor.bootstrap()
    }

    @AfterAll fun stop() = database.close()

    private fun block(number: Long): Block =
        BlockFixtures.BLOCK_NO_CLAUSES.copy(
            number = number,
            id = "0x" + number.toString(16).padStart(64, '0'),
            parentID = "0x" + (number - 1).toString(16).padStart(64, '0'),
        )

    private fun state(block: Block, status: DelegationStatus) =
        Delegation(
            id = "1",
            validator = "0x" + "1".repeat(40),
            tokenId = "9",
            owner = "0x" + "a".repeat(40),
            status = status,
            tokenLevel = TokenLevel.Strength,
            stakedAmount = "0",
            totalRewardsClaimed = BigInteger.ZERO,
            txId = block.id,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
        )

    private fun process(block: Block, updates: List<Delegation>) = runBlocking {
        coEvery { service.processBlock(block, emptyList()) } returns updates
        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )
    }

    @Test
    fun `states are written, resumed from and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val (b10, b11, b12) = listOf(block(10), block(11), block(12))

        process(b10, listOf(state(b10, DelegationStatus.QUEUED)))
        process(b11, emptyList())
        process(b12, listOf(state(b12, DelegationStatus.ACTIVE)))

        assertEquals(
            listOf(state(b12, DelegationStatus.ACTIVE)),
            writer.findByTokenIdIn(listOf("9")),
        )
        assertEquals(2, database.count("delegation.state"))
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any()) }

        processor.rollback(11)

        assertEquals(
            listOf(state(b10, DelegationStatus.QUEUED)),
            writer.findByTokenIdIn(listOf("9")),
        )
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
    }
}
