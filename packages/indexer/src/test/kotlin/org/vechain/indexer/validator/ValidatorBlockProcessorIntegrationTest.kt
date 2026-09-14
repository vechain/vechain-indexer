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
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor on a real schema, the ledger service mocked: rows in, rows and resume out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorBlockProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ValidatorBlockWriteRepository
    private val service = mockk<ValidatorBlockService>()
    private lateinit var processor: ValidatorBlockProcessor

    private val alice = "0x" + "a".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = ValidatorBlockWriteRepository(database.jdbc)
        every { service.save(any()) } answers { writer.save(firstArg()) }
        every { service.invalidateCache() } returns Unit
        processor =
            ValidatorBlockProcessor(
                service,
                writer,
                IndexerStateRepository(database.jdbc),
                CheckpointProperties().apply { saveIntervalSeconds = 0 },
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

    private fun reward(block: Block) =
        ValidatorBlock(
            id = "${block.number}-$alice",
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            validator = alice,
            blockReward = BigInteger.TEN,
            status = BlockStatus.VALIDATED,
        )

    private fun process(block: Block, records: List<ValidatorBlock>) = runBlocking {
        coEvery { service.processBlock(block, emptyList()) } returns records
        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )
    }

    private fun blocks(): List<Long> =
        database.jdbc.queryForList(
            "SELECT block_number FROM validator_block.slot ORDER BY block_number",
            Long::class.javaObjectType,
        )

    @Test
    fun `rows are written, resumed from and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val (b10, b11, b12) = listOf(block(10), block(11), block(12))

        process(b10, listOf(reward(b10)))
        process(b11, emptyList())
        process(b12, listOf(reward(b12)))

        assertEquals(listOf(10L, 12L), blocks())
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any()) }

        processor.rollback(11)

        assertEquals(listOf(10L), blocks())
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
    }
}
