package org.vechain.indexer.performance.accounts

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.TotalAccountsProcessor
import org.vechain.indexer.accounts.TotalAccountsService
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.PostgresPruner

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("total-accounts")
class TotalAccountsProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var totalAccountsRepository: TotalAccountsRepository
    @Autowired lateinit var totalAccountsService: TotalAccountsService
    @Autowired lateinit var totalAccountsPruner: PostgresPruner
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        jdbcTemplate.update("DELETE FROM total_accounts")
        println("✓ Cleared accounts database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.TOTAL_ACCOUNTS_INDEXER,
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
                    totalAccountsPruner = totalAccountsPruner,
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
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                TotalAccountsProcessor(
                    repository = totalAccountsRepository,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.TOTAL_ACCOUNTS_INDEXER)
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
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        TotalAccountsProcessor(
            repository = repository,
            service = service,
            indexerVersionService = indexerVersionService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    AccountsProcessor.process (per block)") { super.processEntry(entry) }
        }
    }
}
