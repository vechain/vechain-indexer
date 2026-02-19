package org.vechain.indexer.performance.transaction

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.transaction.TransactionProcessor
import org.vechain.indexer.transaction.TransactionRepository
import org.vechain.indexer.transaction.TransactionService

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("transactions")
class TransactionProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var transactionRepository: TransactionRepository
    @Autowired lateinit var transactionService: TransactionService
    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired lateinit var checkpointService: CheckpointService
    @Autowired lateinit var processorMetrics: ProcessorMetrics

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        transactionRepository.deleteAll()
        println("✓ Cleared transaction database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.TRANSACTION.NAME,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createTransactionIndexer(startBlock, profiler) },
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

    private fun createTransactionIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        val processor =
            if (profiler != null) {
                val profiledService =
                    ProfiledTransactionService(mongoTemplate = mongoTemplate, profiler = profiler)
                ProfiledTransactionProcessor(
                    profiledService = profiledService,
                    repository = transactionRepository,
                    profiler = profiler,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            } else {
                TransactionProcessor(
                    transactionService = transactionService,
                    repository = transactionRepository,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            }

        return IndexerFactory()
            .name(IndexerNames.TRANSACTION.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .abis("abis")
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .excludeVetTransfers()
            .includeFullBlock()
            .build()
    }

    /** Profiled wrapper for TransactionProcessor */
    private class ProfiledTransactionProcessor(
        private val profiledService: ProfiledTransactionService,
        repository: TransactionRepository,
        private val profiler: DetailedProfiler,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        org.vechain.indexer.BaseProcessor(
            repository = repository,
            indexerName = IndexerNames.TRANSACTION.NAME,
            checkpointService = checkpointService,
            collectionName = IndexerNames.TRANSACTION.COLLECTION,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    TransactionProcessor.process (per block)") {
                if (entry !is IndexingResult.Normal) {
                    throw IllegalArgumentException("Block must be a normal block.")
                }

                if (entry.block.transactions.isNotEmpty()) {
                    profiledService.processBlockTransactions(
                        events = entry.events,
                        block = entry.block,
                    )
                }
            }
        }
    }
}
