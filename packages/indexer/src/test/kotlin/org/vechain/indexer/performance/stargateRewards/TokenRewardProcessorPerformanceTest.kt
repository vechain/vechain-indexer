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
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.rewards.TokenRewardProcessor
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("token-reward", "delegation")
class TokenRewardProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var tokenRewardRepository: TokenRewardRepository
    @Autowired lateinit var tokenRewardService: TokenRewardService
    @Autowired lateinit var archiveService: ArchiveService<TokenReward>
    @Autowired lateinit var delegationRepository: DelegationRepository
    @Autowired lateinit var checkpointService: CheckpointService

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsContract: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        tokenRewardRepository.deleteAll()
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
                    archiveService = archiveService,
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
                    archiveService = archiveService,
                    profiler = profiler,
                    checkpointService = checkpointService,
                )
            } else {
                TokenRewardProcessor(
                    service = serviceToUse,
                    repository = tokenRewardRepository,
                    archiveService = archiveService,
                    checkpointService = checkpointService,
                )
            }

        return IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD.NAME)
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
        archiveService: ArchiveService<TokenReward>,
        private val profiler: DetailedProfiler,
        checkpointService: CheckpointService,
    ) :
        TokenRewardProcessor(
            service = service,
            repository = repository,
            archiveService = archiveService,
            checkpointService = checkpointService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    TokenRewardProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
