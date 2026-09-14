package org.vechain.indexer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.stargate.rewards.TokenRewardProcessor
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.token.StargateTokenProcessor
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.stargate.token.StargateTokenWriteRepository
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.validator.DelegationProcessor
import org.vechain.indexer.validator.DelegationService
import org.vechain.indexer.validator.DelegationWriteRepository
import org.vechain.indexer.validator.ValidatorBlockProcessor
import org.vechain.indexer.validator.ValidatorBlockService
import org.vechain.indexer.validator.ValidatorBlockWriteRepository
import org.vechain.indexer.validator.ValidatorProcessor
import org.vechain.indexer.validator.ValidatorService
import org.vechain.indexer.validator.ValidatorWriteRepository

/** An override of `resetProcessingState` must still reset the base metrics state. */
class RollbackResetsMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = ProcessorMetrics(registry)
    private val state = mockk<IndexerStateRepository>(relaxed = true)
    private val checkpoints = CheckpointProperties()
    private val horizon = InlineVersioningProperties()

    @Test
    fun `ValidatorProcessor rollback lets replayed blocks record again`() =
        assertReplayRecords(
            IndexerNames.VALIDATOR.NAME,
            ValidatorProcessor(
                mockk<ValidatorService>(relaxed = true),
                mockk<ValidatorWriteRepository>(relaxed = true),
                state,
                checkpoints,
                horizon,
                metrics,
            ),
        )

    @Test
    fun `ValidatorBlockProcessor rollback lets replayed blocks record again`() =
        assertReplayRecords(
            IndexerNames.VALIDATOR_BLOCK.NAME,
            ValidatorBlockProcessor(
                mockk<ValidatorBlockService>(relaxed = true),
                mockk<ValidatorBlockWriteRepository>(relaxed = true),
                state,
                checkpoints,
                metrics,
            ),
        )

    @Test
    fun `DelegationProcessor rollback lets replayed blocks record again`() =
        assertReplayRecords(
            IndexerNames.DELEGATION.NAME,
            DelegationProcessor(
                mockk<DelegationService>(relaxed = true),
                mockk<DelegationWriteRepository>(relaxed = true),
                state,
                checkpoints,
                horizon,
                metrics,
            ),
        )

    @Test
    fun `StargateTokenProcessor rollback lets replayed blocks record again`() =
        assertReplayRecords(
            IndexerNames.STARGATE_TOKEN.NAME,
            StargateTokenProcessor(
                mockk<StargateTokenService>(relaxed = true),
                mockk<StargateTokenWriteRepository>(relaxed = true),
                state,
                checkpoints,
                horizon,
                metrics,
            ),
        )

    @Test
    fun `TokenRewardProcessor rollback lets replayed blocks record again`() =
        assertReplayRecords(
            IndexerNames.TOKEN_REWARD.NAME,
            TokenRewardProcessor(
                mockk<TokenRewardService>(relaxed = true),
                mockk<TokenRewardWriteRepository>(relaxed = true),
                state,
                checkpoints,
                horizon,
                metrics,
            ),
        )

    private fun assertReplayRecords(indexerName: String, processor: BaseProcessor) {
        process(processor, 100)
        processor.rollback(50)
        process(processor, 60)

        val timer = registry.find("processor_duration").tag("indexer_name", indexerName).timer()
        assertEquals(2, timer?.count())
    }

    // BaseProcessor records in a finally, so a service that rejects the bare fixture still counts.
    private fun process(processor: BaseProcessor, number: Long) = runBlocking {
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = number)
        runCatching {
            processor.process(
                IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
            )
        }
        Unit
    }
}
