package org.vechain.indexer.performance.blockUsage

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.explorer.BlockUsageProcessor
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("block-usage")
class BlockUsageProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var blockUsageRepository: BlockUsageRepository
    @Autowired lateinit var blockUsageService: BlockUsageService

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        blockUsageRepository.deleteAll()
        println("✓ Cleared block usage database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.BLOCK_USAGE,
                startBlock = 0L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createBlockUsageIndexer(startBlock, profiler) },
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

    private fun createBlockUsageIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledBlockUsageService(repository = blockUsageRepository, profiler = profiler)
            } else {
                blockUsageService
            }

        val processor =
            if (profiler != null) {
                ProfiledBlockUsageProcessor(
                    repository = blockUsageRepository,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                BlockUsageProcessor(
                    repository = blockUsageRepository,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.BLOCK_USAGE)
            .thorClient(thorClient)
            .processor(processor)
            .syncLoggerInterval(100L)
            .startBlock(startBlock)
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for BlockUsageProcessor */
    private class ProfiledBlockUsageProcessor(
        repository: BlockUsageRepository,
        service: BlockUsageService,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        BlockUsageProcessor(
            repository = repository,
            service = service,
            indexerVersionService = indexerVersionService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    BlockUsageProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
