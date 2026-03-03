package org.vechain.indexer.performance.delegation

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.validator.DelegationConfig
import org.vechain.indexer.validator.DelegationProcessor
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationService

@Disabled("Performance test - run explicitly with --tests when needed")
@ActiveProfiles("delegation")
class DelegationProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var delegationRepository: DelegationRepository
    @Autowired lateinit var delegationService: DelegationService
    @Autowired lateinit var inlineVersioningProperties: InlineVersioningProperties
    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired lateinit var checkpointService: CheckpointService
    @Autowired lateinit var processorMetrics: ProcessorMetrics
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
        println("✓ Cleared delegation database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.DELEGATION.NAME,
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
                    mongoTemplate = mongoTemplate,
                    inlineVersioningProperties = inlineVersioningProperties,
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
                    mongoTemplate = mongoTemplate,
                    service = serviceToUse,
                    profiler = profiler,
                    checkpointService = checkpointService,
                    processorMetrics = processorMetrics,
                )
            } else {
                DelegationProcessor(
                    repository = delegationRepository,
                    mongoTemplate = mongoTemplate,
                    checkpointService = checkpointService,
                    service = serviceToUse,
                    processorMetrics = processorMetrics,
                )
            }

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
        mongoTemplate: MongoTemplate,
        service: DelegationService,
        private val profiler: DetailedProfiler,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        DelegationProcessor(
            repository = repository,
            mongoTemplate = mongoTemplate,
            checkpointService = checkpointService,
            service = service,
            processorMetrics = processorMetrics,
        ) {
        override suspend fun processEntry(entry: IndexingResult) {
            profiler.time("    DelegationProcessor.process (per block)") {
                super.processEntry(entry)
            }
        }
    }
}
