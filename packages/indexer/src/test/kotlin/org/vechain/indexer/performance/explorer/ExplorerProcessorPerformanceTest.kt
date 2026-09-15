package org.vechain.indexer.performance.explorer

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.AverageFeesPerUserService
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.ExplorerProcessor
import org.vechain.indexer.explorer.ExplorerWriteRepository
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.postgres.IndexerStateRepository

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("explorer")
class ExplorerProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var explorerRepository: ExplorerWriteRepository
    @Autowired lateinit var blockUsageService: BlockUsageService
    @Autowired lateinit var feesService: AverageFeesPerUserService
    @Autowired lateinit var indexerState: IndexerStateRepository
    @Autowired lateinit var checkpointProperties: CheckpointProperties
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        explorerRepository.truncate()
        println("✓ Cleared explorer schema")

        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.EXPLORER.NAME,
                startBlock = 0L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createExplorerIndexer(startBlock, profiler) },
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

    private fun createExplorerIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        val usage =
            if (profiler != null) ProfiledBlockUsageService(explorerRepository, profiler)
            else blockUsageService
        val fees =
            if (profiler != null) ProfiledAverageFeesPerUserService(explorerRepository, profiler)
            else feesService

        val processor =
            if (profiler != null) {
                ProfiledExplorerProcessor(
                    blockUsageService = usage,
                    feesService = fees,
                    repository = explorerRepository,
                    profiler = profiler,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = InlineVersioningProperties(),
                    processorMetrics = processorMetrics,
                )
            } else {
                ExplorerProcessor(
                    blockUsageService = usage,
                    feesService = fees,
                    repository = explorerRepository,
                    state = indexerState,
                    checkpointProperties = checkpointProperties,
                    horizon = InlineVersioningProperties(),
                    processorMetrics = processorMetrics,
                )
            }

        return IndexerFactory()
            .name(IndexerNames.EXPLORER.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(100L)
            .startBlock(startBlock)
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for ExplorerProcessor */
    private class ProfiledExplorerProcessor(
        blockUsageService: BlockUsageService,
        feesService: AverageFeesPerUserService,
        repository: ExplorerWriteRepository,
        private val profiler: DetailedProfiler,
        state: IndexerStateRepository,
        checkpointProperties: CheckpointProperties,
        horizon: InlineVersioningProperties,
        processorMetrics: ProcessorMetrics,
    ) :
        ExplorerProcessor(
            blockUsageService = blockUsageService,
            feesService = feesService,
            repository = repository,
            state = state,
            checkpointProperties = checkpointProperties,
            horizon = horizon,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    ExplorerProcessor.process (per block)") { super.processEntry(entry) }
        }
    }
}
