package org.vechain.indexer.stargate.rewards

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
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor on a real schema, service mocked: trackers in, current rows and resume out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenRewardProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: TokenRewardWriteRepository
    private val service = mockk<TokenRewardService>()
    private lateinit var processor: TokenRewardProcessor

    private val validator = "0x" + "1".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = TokenRewardWriteRepository(database.jdbc)
        every { service.save(any()) } answers { writer.save(firstArg()) }
        every { service.invalidateCache() } returns Unit
        processor =
            TokenRewardProcessor(
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

    private fun tracker(block: Block, rewards: Long) =
        TokenReward(
            id = "$validator-7",
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            tokenId = "7",
            cycle = 1,
            validator = validator,
            rewards = BigInteger.valueOf(rewards),
            rewardPeriod = RewardPeriod.ALL,
            dayOfMonth = 25,
            weekOfYear = 43,
            month = 10,
            year = 2025,
        )

    private fun process(block: Block, updates: List<TokenReward>) = runBlocking {
        coEvery { service.processBlock(block, emptyList()) } returns updates
        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )
    }

    @Test
    fun `trackers are written, resumed from and rolled back`() {
        assertNull(processor.getLastSyncedBlock())
        val (b10, b11, b12) = listOf(block(10), block(11), block(12))

        process(b10, listOf(tracker(b10, 100)))
        process(b11, emptyList())
        process(b12, listOf(tracker(b12, 120)))

        assertEquals(listOf(tracker(b12, 120)), writer.findAllById(listOf("$validator-7")))
        assertEquals(2, database.count("token_reward.state"))
        assertEquals(BlockIdentifier(12, b12.id), processor.getLastSyncedBlock())
        verify(exactly = 2) { service.save(any()) }

        processor.rollback(11)

        assertEquals(listOf(tracker(b10, 100)), writer.findAllById(listOf("$validator-7")))
        assertEquals(BlockIdentifier(10, null), processor.getLastSyncedBlock())
        verify(exactly = 1) { service.invalidateCache() }
    }
}
