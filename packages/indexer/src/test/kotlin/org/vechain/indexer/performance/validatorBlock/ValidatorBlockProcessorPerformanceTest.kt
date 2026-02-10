package org.vechain.indexer.performance.validatorBlock

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.validator.ValidatorBlockConfig
import org.vechain.indexer.validator.ValidatorBlockProcessor
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validator.ValidatorBlockService

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("validator-reward")
class ValidatorBlockProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var validatorBlockRepository: ValidatorBlockRepository
    @Autowired lateinit var validatorBlockService: ValidatorBlockService

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsAddress: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        validatorBlockRepository.deleteAll()
        println("✓ Cleared validator block database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.VALIDATOR_BLOCK,
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

    private fun createValidatorBlockIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): BlockIndexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledValidatorBlockService(
                    repository = validatorBlockRepository,
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
                )
            } else {
                ValidatorBlockProcessor(
                    service = serviceToUse,
                    repository = validatorBlockRepository,
                )
            }

        return ValidatorBlockConfig()
            .validatorBlockIndexer(
                thorClient = thorClient,
                processor = processor,
                startBlock = startBlock,
                syncLoggerInterval = 100L,
                syncBlockBatchSize = 1L,
                bEProperties = businessEventProperties,
                getAllValidatorsAddress = getAllValidatorsAddress,
            )
    }

    /** Profiled wrapper for ValidatorBlockProcessor */
    private class ProfiledValidatorBlockProcessor(
        service: ValidatorBlockService,
        repository: ValidatorBlockRepository,
        private val profiler: DetailedProfiler,
    ) : ValidatorBlockProcessor(service = service, repository = repository) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    ValidatorBlockProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
