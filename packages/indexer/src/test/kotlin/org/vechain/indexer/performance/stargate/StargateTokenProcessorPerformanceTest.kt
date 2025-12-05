package org.vechain.indexer.performance.stargate

import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.BasePerformanceTest
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.token.StargateEventService
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenArchive
import org.vechain.indexer.stargate.token.StargateTokenProcessor
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.domain.ValidatorDecoder

@ActiveProfiles("stargate-token")
class StargateTokenProcessorPerformanceTest : BasePerformanceTest() {

    @Autowired lateinit var stargateTokenRepository: StargateTokenRepository
    @Autowired lateinit var stargateTokenService: StargateTokenService
    @Autowired lateinit var stargateEventService: StargateEventService
    @Autowired lateinit var validatorDelegationService: ValidatorDelegationService
    @Autowired lateinit var archiveService: ArchiveService<StargateToken, StargateTokenArchive>

    @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
    lateinit var stargateNftContract: String

    @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
    lateinit var stargateDelegationContract: String

    @Value("\${business-event.substitutions.STARGATE_CONTRACT}")
    lateinit var stargateContract: String

    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    lateinit var getAllValidatorsContract: String

    @Value("\${business-event.substitutions.NODE_MANAGEMENT_CONTRACT}")
    lateinit var nodeManagementContract: String

    @Test
    fun `Performance test - 1000 blocks from mainnet`() {
        // Clear database to start fresh
        stargateTokenRepository.deleteAll()
        println("✓ Cleared stargate token database")

        // Create profiler for detailed timing analysis
        val profiler = DetailedProfiler()

        val config =
            PerformanceTestConfig(
                indexerName = IndexerNames.STARGATE_TOKEN,
                startBlock = 23430500L,
                blockCount = 1000,
                warmupBlocks = 0,
            )

        val metrics =
            runPerformanceTest(
                config = config,
                indexerBuilder = { startBlock -> createStargateTokenIndexer(startBlock, profiler) },
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

    private fun createStargateTokenIndexer(
        startBlock: Long,
        profiler: DetailedProfiler? = null,
    ): Indexer {
        // Create profiled service and processor when profiler is provided
        val serviceToUse =
            if (profiler != null) {
                ProfiledStargateTokenService(
                    repository = stargateTokenRepository,
                    eventService = stargateEventService,
                    validatorDelegationService = validatorDelegationService,
                    archiveService = archiveService,
                    profiler = profiler,
                )
            } else {
                stargateTokenService
            }

        val processor =
            if (profiler != null) {
                ProfiledStargateTokenProcessor(
                    service = serviceToUse,
                    stargateTokenRepository = stargateTokenRepository,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                    profiler = profiler,
                )
            } else {
                StargateTokenProcessor(
                    service = serviceToUse,
                    stargateTokenRepository = stargateTokenRepository,
                    archiveService = archiveService,
                    indexerVersionService = mockIndexerVersionService,
                )
            }

        every { mockIndexerVersionService.getLastProcessedBlock(any()) } returns null

        return IndexerFactory()
            .name(IndexerNames.STARGATE_TOKEN)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(100L)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(
                listOf(
                    stargateNftContract,
                    stargateDelegationContract,
                    stargateContract,
                    nodeManagementContract,
                )
            )
            .abiEventNames(
                listOf(
                    "TokenMinted",
                    "TokenBurned",
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "Transfer",
                    "TokenManagerAdded",
                    "TokenManagerRemoved",
                    "MaturityPeriodBoosted",
                    "ValidationSignaledExit",
                    "DelegationRewardsClaimed",
                    "BaseVTHORewardsClaimed",
                    "NodeDelegated",
                )
            )
            .callDataClauses(listOf(ValidatorDecoder.buildClauses(getAllValidatorsContract)[0]))
            .excludeVetTransfers()
            .build()
    }

    /** Profiled wrapper for StargateTokenProcessor */
    private class ProfiledStargateTokenProcessor(
        service: StargateTokenService,
        stargateTokenRepository: StargateTokenRepository,
        archiveService: ArchiveService<StargateToken, StargateTokenArchive>,
        indexerVersionService: org.vechain.indexer.version.IndexerVersionService,
        private val profiler: DetailedProfiler,
    ) :
        StargateTokenProcessor(
            service = service,
            stargateTokenRepository = stargateTokenRepository,
            archiveService = archiveService,
            indexerVersionService = indexerVersionService,
        ) {
        override fun process(entry: IndexingResult) {
            profiler.time("    StargateTokenProcessor.process (per block)") { super.process(entry) }
        }
    }
}
