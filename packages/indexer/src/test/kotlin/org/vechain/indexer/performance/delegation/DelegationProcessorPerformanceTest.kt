package org.vechain.indexer.performance.delegation

import io.mockk.every
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationArchive
import org.vechain.indexer.validator.DelegationConfig
import org.vechain.indexer.validator.DelegationProcessor
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationService

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("delegation")
class DelegationProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var delegationRepository: DelegationRepository
    @Autowired lateinit var delegationService: DelegationService
    @Autowired lateinit var archiveService: ArchiveService<Delegation, DelegationArchive>
    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired
    lateinit var validatorDelegationService:
        org.vechain.indexer.validator.ValidatorDelegationService

    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    lateinit var builtinStakerAddress: String

    @Value("\${business-event.substitutions.STARGATE_CONTRACT}")
    lateinit var stargateContract: String

    @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
    lateinit var stargateNftContract: String

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsAddress: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        delegationRepository.deleteAll()
        mongoTemplate.remove(
            org.springframework.data.mongodb.core.query.Query(),
            DelegationArchive::class.java,
        )
        println("✓ Cleared delegation database and archives")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.DELEGATION,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createDelegationIndexer(startBlock, profiler) },
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

    private fun createDelegationIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledDelegationService(
                    repository = delegationRepository,
                    archiveService = archiveService,
                    validatorDelegationService = validatorDelegationService,
                    stakerSC = builtinStakerAddress,
                    profiler = profiler,
                )
            } else {
                delegationService
            }

        val processor =
            if (profiler != null) {
                ProfiledDelegationProcessor(
                    repository = delegationRepository,
                    archiveService = archiveService,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                DelegationProcessor(
                    repository = delegationRepository,
                    archiveService = archiveService,
                    service = serviceToUse,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return DelegationConfig()
            .delegationIndexer(
                thorClient = thorClient,
                processor = processor,
                startBlock = startBlock,
                syncLogInterval = 100L,
                channelBatchSize = 1,
                builtinStakerAddress = builtinStakerAddress,
                stargateContract = stargateContract,
                stargateNftContract = stargateNftContract,
                getAllValidatorsAddress = getAllValidatorsAddress,
            )
    }

    /** Profiled wrapper for DelegationProcessor */
    private class ProfiledDelegationProcessor(
        repository: DelegationRepository,
        archiveService: ArchiveService<Delegation, DelegationArchive>,
        service: DelegationService,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        DelegationProcessor(
            repository = repository,
            archiveService = archiveService,
            service = service,
            indexerVersionService = indexerVersionService,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    DelegationProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
