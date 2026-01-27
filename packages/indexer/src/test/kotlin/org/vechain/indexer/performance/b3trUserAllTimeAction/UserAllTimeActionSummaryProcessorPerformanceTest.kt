package org.vechain.indexer.performance.b3trUserAllTimeAction

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.action.ActionImpactConfig
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummaryProcessor
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummaryService
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.PostgresPruner

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("b3tr-user-all-time-action-summary")
class UserAllTimeActionSummaryProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var repository: UserAllTimeActionSummaryRepository
    @Autowired lateinit var service: UserAllTimeActionSummaryService
    @Autowired lateinit var pruner: PostgresPruner
    @Autowired lateinit var impactConfig: ActionImpactConfig

    @Value("\${business-event.substitutions.B3TR_CONTRACT}") lateinit var b3trContract: String

    @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
    lateinit var x2earnRewardsPoolContract: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Note: Database clearing should be done via SQL or test setup

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.USER_ALL_TIME_ACTION_SUMMARY,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createIndexer(startBlock, profiler) },
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

    private fun createIndexer(startBlock: Long, profiler: DetailedProfiler? = null): Indexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledUserAllTimeActionSummaryService(
                    repository = repository,
                    impactConfig = impactConfig,
                    pruner = pruner,
                    profiler = profiler,
                )
            } else {
                service
            }

        val processor =
            if (profiler != null) {
                ProfiledUserAllTimeActionSummaryProcessor(
                    repository = repository,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                UserAllTimeActionSummaryProcessor(
                    repository = repository,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.USER_ALL_TIME_ACTION_SUMMARY)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .blockBatchSize(1L)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ActionReward"))
            .businessEventContracts(listOf(b3trContract, x2earnRewardsPoolContract))
            .businessEventSubstitutionParams(businessEventProperties.substitutions)
            .build()
    }

    /** Profiled wrapper for UserAllTimeActionSummaryProcessor */
    private class ProfiledUserAllTimeActionSummaryProcessor(
        repository: UserAllTimeActionSummaryRepository,
        service: UserAllTimeActionSummaryService,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        UserAllTimeActionSummaryProcessor(
            repository = repository,
            service = service,
            indexerVersionService = indexerVersionService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    UserAllTimeActionSummaryProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
