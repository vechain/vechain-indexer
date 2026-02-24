package org.vechain.indexer.performance.accounts

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.TotalAccounts
import org.vechain.indexer.accounts.TotalAccountsArchive
import org.vechain.indexer.accounts.TotalAccountsProcessor
import org.vechain.indexer.accounts.TotalAccountsService
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("total-accounts")
class TotalAccountsProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var totalAccountsRepository: TotalAccountsRepository
    @Autowired lateinit var totalAccountsService: TotalAccountsService
    @Autowired lateinit var archiveService: ArchiveService<TotalAccounts, TotalAccountsArchive>
    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired lateinit var checkpointService: CheckpointService
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        totalAccountsRepository.deleteAll()
        println("✓ Cleared accounts database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.TOTAL_ACCOUNTS.NAME,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createAccountsIndexer(startBlock, profiler) },
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

    private fun createAccountsIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): BlockIndexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledTotalAccountsService(
                    repository = totalAccountsRepository,
                    archiveService = archiveService,
                    profiler = profiler,
                )
            } else {
                totalAccountsService
            }

        val processor =
            if (profiler != null) {
                ProfiledTotalAccountsProcessor(
                    service = serviceToUse,
                    repository = totalAccountsRepository,
                    archiveService = archiveService,
                    profiler = profiler,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            } else {
                TotalAccountsProcessor(
                    service = serviceToUse,
                    repository = totalAccountsRepository,
                    archiveService = archiveService,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            }

        return IndexerFactory()
            .name(IndexerNames.TOTAL_ACCOUNTS.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .blockBatchSize(1L)
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for TotalAccountsProcessor */
    private class ProfiledTotalAccountsProcessor(
        service: TotalAccountsService,
        repository: TotalAccountsRepository,
        archiveService: ArchiveService<TotalAccounts, TotalAccountsArchive>,
        private val profiler: DetailedProfiler,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        TotalAccountsProcessor(
            service = service,
            repository = repository,
            archiveService = archiveService,
            checkpointService = checkpointService,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    AccountsProcessor.process (per block)") { super.processEntry(entry) }
        }
    }
}
