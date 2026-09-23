package org.vechain.indexer.validator

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
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

/** The processor on a real schema, both services mocked: states and slots in, rows out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ValidatorWriteRepository
    private lateinit var reader: ValidatorReadRepository
    private val service = mockk<ValidatorService>()
    private val blockService = mockk<ValidatorBlockService>()
    private lateinit var slots: ValidatorBlockWriteRepository
    private lateinit var processor: ValidatorProcessor

    private val alice = "0x" + "a".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = ValidatorWriteRepository(database.jdbc)
        reader = ValidatorReadRepository(database.jdbc)
        slots = ValidatorBlockWriteRepository(database.jdbc)
        every { service.save(any(), any()) } answers
            {
                writer.save(firstArg())
                slots.save(secondArg())
            }
        every { service.invalidateCache() } returns Unit
        every { blockService.invalidateCache() } returns Unit
        processor =
            ValidatorProcessor(
                service,
                blockService,
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

    private fun state(block: Block, stake: String) =
        Validator(
            id = alice,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            status = org.vechain.indexer.validator.Status.ACTIVE,
            validatorVetStaked = BigDecimal(stake),
        )

    private fun slot(block: Block, validator: String) =
        ValidatorBlock(
            id = "${block.number}-$validator",
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            validator = validator,
            status = BlockStatus.VALIDATED,
        )

    private fun process(block: Block, updates: List<Validator>) = runBlocking {
        coEvery { service.processBlock(block, emptyList()) } returns updates
        coEvery { blockService.processBlock(block, emptyList(), updates) } returns
            updates.map { slot(block, it.id) }
        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )
    }

    @Test
    fun `states are written, resumed from and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val (b10, b11, b12) = listOf(block(10), block(11), block(12))

        process(b10, listOf(state(b10, "100")))
        process(b11, emptyList())
        process(b12, listOf(state(b12, "120")))

        assertEquals(listOf(state(b12, "120")), reader.findAll())
        assertEquals(2, database.count("validator.state"))
        assertEquals(2, database.count("validator.slot"))
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any(), any()) }

        processor.rollback(11)

        assertEquals(listOf(state(b10, "100")), reader.findAll())
        assertEquals(1, database.count("validator.slot"))
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
        // Once from bootstrap, once from the rollback.
        verify(exactly = 2) { blockService.invalidateCache() }
    }
}
