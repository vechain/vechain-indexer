package org.vechain.indexer.stargate.token

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
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor on a real schema, service mocked: snapshots in, rows and resume point out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StargateTokenProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: StargateTokenWriteRepository
    private val service = mockk<StargateTokenService>()
    private lateinit var processor: StargateTokenProcessor

    @BeforeAll
    fun start() {
        database.start()
        writer = StargateTokenWriteRepository(database.jdbc)
        every { service.save(any()) } answers { writer.save(firstArg()) }
        every { service.invalidateCache() } returns Unit
        processor =
            StargateTokenProcessor(
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

    private fun snapshot(block: Block, owner: String) =
        StargateToken(
            tokenId = "7",
            level = TokenLevel.Strength,
            owner = owner,
            delegationStatus = org.vechain.indexer.validator.Status.NONE,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger.TEN,
            migrated = false,
            boosted = false,
            blockNumber = block.number,
            blockId = block.id,
            blockTimestamp = block.timestamp,
        )

    private fun process(block: Block, updates: List<StargateToken>) = runBlocking {
        coEvery { service.processBlock(block, emptyList()) } returns updates
        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )
    }

    @Test
    fun `snapshots are written, resumed from and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val (b10, b11, b12) = listOf(block(10), block(11), block(12))
        val alice = "0x" + "a".repeat(40)
        val bob = "0x" + "b".repeat(40)

        process(b10, listOf(snapshot(b10, alice)))
        process(b11, emptyList())
        process(b12, listOf(snapshot(b12, bob)))

        assertEquals(listOf(snapshot(b12, bob)), writer.findAllById(setOf("7")))
        assertEquals(2, database.count("stargate_token.state"))
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any()) }

        processor.rollback(11)

        assertEquals(listOf(snapshot(b10, alice)), writer.findAllById(setOf("7")))
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
    }
}
