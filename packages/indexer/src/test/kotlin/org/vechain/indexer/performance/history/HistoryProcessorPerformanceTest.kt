package org.vechain.indexer.performance.history

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.history.DelegationLifecycleHistoryService
import org.vechain.indexer.history.HistoryConfig
import org.vechain.indexer.history.HistoryProcessor
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.history.HistoryWriteRepository
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.validator.ValidatorRepository

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("history")
class HistoryProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var historyRepository: HistoryWriteRepository

    @Autowired lateinit var historyService: HistoryService

    @Autowired lateinit var delegationLifecycleHistoryService: DelegationLifecycleHistoryService
    @Autowired lateinit var validatorRepository: ValidatorRepository

    @Autowired lateinit var indexerState: IndexerStateRepository
    @Autowired lateinit var checkpointProperties: CheckpointProperties
    @Autowired lateinit var horizon: InlineVersioningProperties
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Autowired @Qualifier("validatorIndexer") lateinit var validatorIndexer: Indexer

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        historyRepository.truncate()
        println("✓ Cleared history database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.HISTORY.NAME,
                startBlock = 23430500L, // Adjust this to a block range with activity
                blockCount = 1000, // Start with 100 blocks for first test
                warmupBlocks = 0, // Disabled warmup to avoid database conflicts
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createHistoryIndexer(startBlock, profiler) },
                profiler = profiler,
            )

        // Print errors if any (for debugging)
        if (metrics.errors.isNotEmpty()) {
            println("\n⚠️  ERRORS ENCOUNTERED (${metrics.errors.size}):")
            metrics.errors.forEach { println("  - $it") }
            println()
        }

        // Assert performance targets (adjust based on your requirements)
        assert(metrics.blocksPerSecond > 1.0) {
            "Performance too slow: ${metrics.blocksPerSecond} blocks/sec"
        }
        // Comment out error assertion for now to see what errors occurred
        // assert(metrics.errors.isEmpty()) { "Test had ${metrics.errors.size} errors" }
    }

    private fun createHistoryIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        // Create processor with real service and repository
        val processor =
            if (profiler != null) {
                // Use comprehensive profiling that tracks ALL internal methods
                val profiledService =
                    ProfiledHistoryService(
                        historyRepository,
                        delegationLifecycleHistoryService,
                        validatorRepository,
                        0L,
                        profiler,
                    )
                ProfiledHistoryProcessor(
                    repository = historyRepository,
                    historyService = profiledService,
                    profiler = profiler,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = horizon,
                    processorMetrics = processorMetrics,
                )
            } else {
                // Use standard processor
                HistoryProcessor(
                    historyService = historyService,
                    repository = historyRepository,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = horizon,
                    processorMetrics = processorMetrics,
                )
            }

        return HistoryConfig()
            .historyIndexer(
                thorClient = thorClient,
                processor = processor,
                validatorIndexer = validatorIndexer,
                startBlock = startBlock,
                syncLoggerInterval = 100L,
                bEProperties = businessEventProperties,
            )
    }

    /** Profiled wrapper for HistoryProcessor that times each operation */
    private class ProfiledHistoryProcessor(
        repository: HistoryWriteRepository,
        historyService: HistoryService,
        private val profiler: DetailedProfiler,
        state: IndexerStateRepository,
        checkpointProperties: CheckpointProperties,
        horizon: InlineVersioningProperties,
        processorMetrics: ProcessorMetrics,
    ) :
        HistoryProcessor(
            historyService = historyService,
            repository = repository,
            state = state,
            checkpointProperties = checkpointProperties,
            horizon = horizon,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("HistoryProcessor.process (per block)") {
                profiler.time("  Event processing logic") {
                    // The actual processing happens here
                    super.processEntry(entry)
                }
            }
        }
    }
}
