package org.vechain.indexer.performance.stargateRewards

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.stargate.rewards.TokenRewardProcessor
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("token-reward", "delegation")
class TokenRewardProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var tokenRewardRepository: TokenRewardRepository
    @Autowired lateinit var tokenRewardService: TokenRewardService
    @Autowired lateinit var tokenRewardPruner: PostgresPruner
    @Autowired lateinit var delegationRepository: DelegationRepository

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsContract: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Note: With PostgreSQL, database cleanup is handled differently
        println("✓ Starting token reward performance test")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.TOKEN_REWARD,
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
                    tokenRewardPruner = tokenRewardPruner,
                    delegationRepository = delegationRepository,
                    thorClient = thorClient,
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
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                TokenRewardProcessor(
                    service = serviceToUse,
                    repository = tokenRewardRepository,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .blockBatchSize(1L)
            .callDataClauses(ValidatorDecoder.buildClauses(getAllValidatorsContract))
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for TokenRewardProcessor */
    private class ProfiledTokenRewardProcessor(
        service: TokenRewardService,
        repository: TokenRewardRepository,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        TokenRewardProcessor(
            service = service,
            repository = repository,
            indexerVersionService = indexerVersionService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    TokenRewardProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
