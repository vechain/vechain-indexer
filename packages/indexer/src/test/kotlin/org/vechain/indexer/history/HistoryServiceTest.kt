package org.vechain.indexer.history

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.SimpleBlockIndexerCoordinator
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService

@ExtendWith(MockKExtension::class)
class HistoryServiceTest {
    @MockK lateinit var historyRepository: HistoryRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var blacklistClient: NftBlacklistClient

    @MockK lateinit var validatorDelegationService: ValidatorDelegationService

    @MockK lateinit var thorClient: ThorClient

    @MockK lateinit var processor: HistoryProcessor

    @MockK lateinit var businessEventProperties: BusinessEventProperties

    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        every { businessEventProperties.substitutions } returns BUSINESS_EVENT_PARAMS
        every { mongoTemplate.getCollectionName(IndexedHistoryEvent::class.java) } returns "history"
        every {
            mongoTemplate.aggregate(any<Aggregation>(), "history", IndexedHistoryEvent::class.java)
        } returns AggregationResults(emptyList(), Document())
        every { processor.getLastSyncedBlock() } returns null
        coEvery { processor.rollback(any()) } returns Unit
        coEvery { processor.process(any()) } returns Unit
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(InspectionResult("0x", emptyList(), emptyList(), 0, false, ""))
        coEvery { blacklistClient.isBlacklisted(any(), any()) } returns false
        every { validatorDelegationService.decodeValidatorSnapshots(any()) } returns emptyMap()
        coEvery { validatorDelegationService.resolveCycleInfo(any(), any(), any()) } answers
            {
                5L to (secondArg<Long>() + 5L)
            }
        every { validatorDelegationService.resolveNextCycleBlock(any(), any(), any()) } answers
            {
                thirdArg<Long>() + 5L
            }

        val delegationLifecycleHistoryService =
            DelegationLifecycleHistoryService(
                mongoTemplate = mongoTemplate,
                validatorDelegationService = validatorDelegationService,
                stakerSC = "0x00000000000000000000000000005374616B6572",
                stargateNftContract = BUSINESS_EVENT_PARAMS.getValue("STARGATE_NFT_CONTRACT"),
            )

        historyService =
            HistoryService(
                historyRepository = historyRepository,
                mongoTemplate = mongoTemplate,
                blacklistClient = blacklistClient,
                delegationLifecycleHistoryService = delegationLifecycleHistoryService,
                validatorDelegationService = validatorDelegationService,
            )
    }

    @Test
    fun `processBlock attaches lifecycle metadata to real stargate exit request row`() =
        runBlocking {
            val results =
                captureIndexerResults(
                    listOf(
                        BlockFixtures.BLOCK_STARGATE_STAKER_DELEGATION,
                        BlockFixtures.BLOCK_STARGATE_DELEGATION_EXIT_REQUEST,
                    )
                )

            val requestResult =
                results.first { blockResult ->
                    blockResult.events().any { it.eventType == "STARGATE_DELEGATE_REQUEST" }
                }
            val exitResult =
                results.first { blockResult ->
                    blockResult.events().any { it.eventType == "STARGATE_DELEGATION_EXIT_REQUEST" }
                }

            historyService.processBlock(
                requestResult.events(),
                requestResult.block,
                requestResult.callResults,
            )
            val exitRecords =
                historyService.processBlock(
                    exitResult.events(),
                    exitResult.block,
                    exitResult.callResults,
                )

            val exitRequest =
                exitRecords.first {
                    it.eventName == HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST
                }

            assertThat(exitRequest.delegationLifecycleStatus).isEqualTo(Status.EXITING)
            assertThat(exitRequest.delegationLifecycleNextCycle)
                .isEqualTo(exitResult.block.number + 5L)
            assertThat(exitRequest.delegationLifecycleCycleLength).isEqualTo(5L)
        }

    private suspend fun captureIndexerResults(
        blocks: List<org.vechain.indexer.thor.model.Block>
    ): List<IndexingResult.BlockResult> {
        val capturedResults = mutableListOf<IndexingResult.BlockResult>()
        coEvery { processor.process(any()) } answers
            {
                val result = firstArg<IndexingResult>()
                if (result is IndexingResult.BlockResult) {
                    capturedResults.add(result)
                }
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = blocks.first().number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                    getAllValidatorsAddress = "0xvalidators",
                )

        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = blocks)

        return capturedResults
    }
}
