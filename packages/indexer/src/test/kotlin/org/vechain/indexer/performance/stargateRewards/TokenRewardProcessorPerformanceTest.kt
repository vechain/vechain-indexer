package org.vechain.indexer.performance.stargateRewards

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.stargate.rewards.TokenRewardProcessor
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.ValidatorReadRepository

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("token-reward", "delegation", "validator")
class TokenRewardProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var tokenRewardRepository: TokenRewardWriteRepository
    @Autowired lateinit var tokenRewardService: TokenRewardService
    @Autowired lateinit var inlineVersioningProperties: InlineVersioningProperties
    @Autowired lateinit var validatorV2Repository: ValidatorReadRepository
    @Autowired lateinit var delegationV2Repository: DelegationReadRepository
    @Autowired lateinit var indexerState: IndexerStateRepository
    @Autowired lateinit var checkpointProperties: CheckpointProperties
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Value("\${indexer.start-block.validator}") var validatorStartBlock: Long = 0L

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        tokenRewardRepository.truncate()
        println("✓ Cleared token reward database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.TOKEN_REWARD.NAME,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createTokenRewardIndexer(startBlock, profiler) },
                profiler = profiler,
            )

        // Print errors if any (for debugging)
        if (metrics.errors.isNotEmpty()) {
            println("\n⚠️  ERRORS ENCOUNTERED (${metrics.errors.size}):")
            metrics.errors.forEach { println("  - $it") }
            println()
        }

        // Assert performance targets
        assert(metrics.blocksPerSecond > 1.0) {
            "Performance too slow: ${metrics.blocksPerSecond} blocks/sec"
        }
    }

    private fun createTokenRewardIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): BlockIndexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledTokenRewardService(
                    repository = tokenRewardRepository,
                    validatorV2Repository = validatorV2Repository,
                    delegationV2Repository = delegationV2Repository,
                    thorClient = thorClient,
                    validatorStartBlock = validatorStartBlock,
                    profiler = profiler,
                )
            } else {
                tokenRewardService
            }

        val processor =
            if (profiler != null) {
                ProfiledTokenRewardProcessor(
                    service = serviceToUse,
                    repository = tokenRewardRepository,
                    profiler = profiler,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = inlineVersioningProperties,
                    processorMetrics = processorMetrics,
                )
            } else {
                TokenRewardProcessor(
                    service = serviceToUse,
                    repository = tokenRewardRepository,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = inlineVersioningProperties,
                    processorMetrics = processorMetrics,
                )
            }

        return IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .callDataClauses(listOf(TokenRewardService.energyTotalSupplyClause()))
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for TokenRewardProcessor */
    private class ProfiledTokenRewardProcessor(
        service: TokenRewardService,
        repository: TokenRewardWriteRepository,
        private val profiler: DetailedProfiler,
        state: IndexerStateRepository,
        checkpointProperties: CheckpointProperties,
        horizon: InlineVersioningProperties,
        processorMetrics: ProcessorMetrics,
    ) :
        TokenRewardProcessor(
            service = service,
            repository = repository,
            state = state,
            checkpointProperties = checkpointProperties,
            horizon = horizon,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    TokenRewardProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
