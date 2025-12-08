package org.vechain.indexer.performance.validator

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorArchive
import org.vechain.indexer.validator.ValidatorConfig
import org.vechain.indexer.validator.ValidatorProcessor
import org.vechain.indexer.validator.ValidatorRepository
import org.vechain.indexer.validator.ValidatorService

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("validator")
class ValidatorProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var validatorRepository: ValidatorRepository
    @Autowired lateinit var validatorService: ValidatorService
    @Autowired lateinit var archiveService: ArchiveService<Validator, ValidatorArchive>
    @Autowired lateinit var thorService: org.vechain.indexer.thor.ThorService

    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    lateinit var builtinStakerAddress: String

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsAddress: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        validatorRepository.deleteAll()
        println("✓ Cleared validator database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.VALIDATOR,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createValidatorIndexer(startBlock, profiler) },
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

    private fun createValidatorIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledValidatorService(
                    repository = validatorRepository,
                    archiveService = archiveService,
                    thorService = thorService,
                    statsStartThreshold = 25L, // Same as validatorStatsThresholdBlocks
                    stakerSC = builtinStakerAddress,
                    profiler = profiler,
                )
            } else {
                validatorService
            }

        val processor =
            if (profiler != null) {
                ProfiledValidatorProcessor(
                    repository = validatorRepository,
                    service = serviceToUse,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                ValidatorProcessor(
                    repository = validatorRepository,
                    service = serviceToUse,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return ValidatorConfig()
            .validatorIndexer(
                thorClient = thorClient,
                processor = processor,
                service = serviceToUse,
                startBlock = startBlock,
                syncLogInterval = 100L,
                syncBlockBatchSize = 50L,
                builtinStakerAddress = builtinStakerAddress,
                getAllValidatorsAddress = getAllValidatorsAddress,
            )
    }

    /** Profiled wrapper for ValidatorProcessor */
    private class ProfiledValidatorProcessor(
        repository: ValidatorRepository,
        service: ValidatorService,
        archiveService: ArchiveService<Validator, ValidatorArchive>,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        ValidatorProcessor(
            repository = repository,
            service = service,
            archiveService = archiveService,
            indexerVersionService = indexerVersionService,
        ) {
        override fun process(entry: IndexingResult) {
            profiler.time("    ValidatorProcessor.process (per block)") { super.process(entry) }
        }
    }
}
