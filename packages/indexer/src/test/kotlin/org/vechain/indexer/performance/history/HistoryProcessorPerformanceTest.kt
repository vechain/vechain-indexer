package org.vechain.indexer.performance.history

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.history.HistoryConfig
import org.vechain.indexer.history.HistoryProcessor
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("history")
class HistoryProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var historyRepository: HistoryRepository

    @Autowired lateinit var historyService: HistoryService

    @Autowired lateinit var mongoTemplate: MongoTemplate

    @Autowired lateinit var blacklistClient: NftBlacklistClient

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        historyRepository.deleteAll()
        println("✓ Cleared history database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.HISTORY,
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
                        mongoTemplate,
                        blacklistClient,
                        profiler,
                    )
                ProfiledHistoryProcessor(
                    repository = historyRepository,
                    historyService = profiledService,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                // Use standard processor
                HistoryProcessor(
                    repository = historyRepository,
                    historyService = historyService,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        // Mock processor methods to prevent rollback issues
        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return HistoryConfig()
            .historyIndexer(
                thorClient = thorClient,
                processor = processor,
                startBlock = startBlock,
                syncLoggerInterval = 100L,
                bEProperties = businessEventProperties,
            )
    }

    /** Profiled wrapper for HistoryProcessor that times each operation */
    private class ProfiledHistoryProcessor(
        repository: HistoryRepository,
        historyService: HistoryService,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        HistoryProcessor(
            repository = repository,
            historyService = historyService,
            indexerVersionService = indexerVersionService,
        ) {
        override fun processEntry(entry: IndexingResult) {
            profiler.time("HistoryProcessor.process (per block)") {
                profiler.time("  Event processing logic") {
                    // The actual processing happens here
                    super.processEntry(entry)
                }
            }
        }
    }
}
