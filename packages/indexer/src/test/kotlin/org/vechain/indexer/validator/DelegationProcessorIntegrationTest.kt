package org.vechain.indexer.validator

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.math.BigDecimal
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
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockService
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedWriteRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor on a real schema with the series live and the delegation service mocked. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegationProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: DelegationWriteRepository
    private val service = mockk<DelegationService>()
    private lateinit var vetDelegated: VetDelegatedByBlockService
    private lateinit var processor: DelegationProcessor

    @BeforeAll
    fun start() {
        database.start()
        writer = DelegationWriteRepository(database.jdbc)
        vetDelegated =
            spyk(VetDelegatedByBlockService(VetDelegatedWriteRepository(database.jdbc), writer))
        every { service.save(any(), any()) } answers
            {
                writer.save(firstArg())
                vetDelegated.save(secondArg(), firstArg())
            }
        every { service.invalidateCache() } returns Unit
        processor =
            DelegationProcessor(
                service,
                vetDelegated,
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
            stakedAmount = "1000",
            totalRewardsClaimed = BigInteger.ZERO,
            txId = block.id,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
        )

    private fun totalAt(block: Long): BigDecimal? =
        database.jdbc.queryForObject(
            "SELECT total FROM delegation.total_by_block WHERE block_number = ?",
            BigDecimal::class.java,
            block,
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
        assertEquals(2, database.count("delegation.total_by_block"))
        // The activation at block 12 is in that block's total.
        assertEquals(BigDecimal(1000), totalAt(12))
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any(), any()) }

        processor.rollback(11)

        assertEquals(
            listOf(state(b10, DelegationStatus.QUEUED)),
            writer.findByTokenIdIn(listOf("9")),
        )
        assertEquals(1, database.count("delegation.total_by_block"))
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
        verify(exactly = 1) { vetDelegated.resetCache() }
    }
}
