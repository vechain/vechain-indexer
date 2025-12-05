package org.vechain.indexer.performance.accounts

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.Accounts
import org.vechain.indexer.accounts.AccountsArchive
import org.vechain.indexer.accounts.AccountsProcessor
import org.vechain.indexer.accounts.AccountsRepository
import org.vechain.indexer.accounts.AccountsService
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("accounts")
class AccountsProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var accountsRepository: AccountsRepository
    @Autowired lateinit var accountsService: AccountsService
    @Autowired lateinit var archiveService: ArchiveService<Accounts, AccountsArchive>

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        accountsRepository.deleteAll()
        archiveService.deleteAll()
        println("✓ Cleared accounts database and archives")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.ACCOUNTS_INDEXER,
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
                ProfiledAccountsService(
                    repository = accountsRepository,
                    archiveService = archiveService,
                    profiler = profiler,
                )
            } else {
                accountsService
            }

        val processor =
            if (profiler != null) {
                ProfiledAccountsProcessor(
                    service = serviceToUse,
                    repository = accountsRepository,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                AccountsProcessor(
                    service = serviceToUse,
                    repository = accountsRepository,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.ACCOUNTS_INDEXER)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .blockBatchSize(1L)
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for AccountsProcessor */
    private class ProfiledAccountsProcessor(
        service: AccountsService,
        repository: AccountsRepository,
        archiveService: ArchiveService<Accounts, AccountsArchive>,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        AccountsProcessor(
            service = service,
            repository = repository,
            archiveService = archiveService,
            indexerVersionService = indexerVersionService,
        ) {
        override fun process(entry: IndexingResult) {
            profiler.time("    AccountsProcessor.process (per block)") { super.process(entry) }
        }
    }
}
