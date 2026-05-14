package org.vechain.indexer.performance.validatorBlock

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.validator.ValidatorBlockProcessor
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validator.ValidatorBlockService
import org.vechain.indexer.validator.ValidatorV2Repository

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("validator-reward", "validator-v2")
class ValidatorBlockProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var validatorBlockRepository: ValidatorBlockRepository
    @Autowired lateinit var validatorBlockService: ValidatorBlockService
    @Autowired lateinit var validatorV2Repository: ValidatorV2Repository
    @Autowired lateinit var checkpointService: CheckpointService
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        validatorBlockRepository.deleteAll()
        println("✓ Cleared validator block database")

        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.VALIDATOR_BLOCK.NAME,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock ->
                    createValidatorBlockIndexer(startBlock, profiler)
                },
                profiler = profiler,
            )

        if (metrics.errors.isNotEmpty()) {
            println("\n⚠️  ERRORS ENCOUNTERED (${metrics.errors.size}):")
            metrics.errors.forEach { println("  - $it") }
            println()
        }

        assert(metrics.blocksPerSecond > 1.0) {
            "Performance too slow: ${metrics.blocksPerSecond} blocks/sec"
        }
    }

    private fun createValidatorBlockIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): BlockIndexer {
        val serviceToUse =
            if (profiler != null) {
                ProfiledValidatorBlockService(
                    repository = validatorBlockRepository,
                    validatorRepository = validatorV2Repository,
                    thorClient = thorClient,
                    profiler = profiler,
                )
            } else {
                validatorBlockService
            }

        val processor =
            if (profiler != null) {
                ProfiledValidatorBlockProcessor(
                    service = serviceToUse,
                    repository = validatorBlockRepository,
                    profiler = profiler,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            } else {
                ValidatorBlockProcessor(
                    service = serviceToUse,
                    repository = validatorBlockRepository,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            }

        // Build the indexer directly (skipping ValidatorBlockConfig) so we don't need the
        // `validatorV2Indexer` bean for ordering — perf test runs this indexer in isolation.
        return IndexerFactory()
            .name(IndexerNames.VALIDATOR_BLOCK.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .callDataClauses(listOf(TokenRewardService.energyTotalSupplyClause()))
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for ValidatorBlockProcessor */
    private class ProfiledValidatorBlockProcessor(
        service: ValidatorBlockService,
        repository: ValidatorBlockRepository,
        private val profiler: DetailedProfiler,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        ValidatorBlockProcessor(
            service = service,
            repository = repository,
            checkpointService = checkpointService,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    ValidatorBlockProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
