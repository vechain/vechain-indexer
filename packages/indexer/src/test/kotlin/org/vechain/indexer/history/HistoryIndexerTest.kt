package org.vechain.indexer.history

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.SimpleBlockIndexerCoordinator
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.thor.client.ThorClient
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@ExtendWith(MockKExtension::class)
class HistoryIndexerTest {
    @MockK lateinit var repository: HistoryRepository

    @MockK lateinit var processor: HistoryProcessor

    @MockK lateinit var thorClient: ThorClient

    @MockK lateinit var businessEventProperties: BusinessEventProperties

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { businessEventProperties.substitutions } returns BUSINESS_EVENT_PARAMS
    }

    @Test
    fun `Indexer with B3TR Block`() {
        runBlocking {
            expectThat(true).isTrue()
            val b3trBlock = BlockFixtures.BLOCK_B3TR_ACTION

            every { processor.getLastSyncedBlock() } returns null

            val indexingResult = slot<IndexingResult>()
            every { processor.rollback(20614874) } returns Unit
            every { processor.process(capture(indexingResult)) } returns Unit

            val indexer =
                HistoryConfig()
                    .historyIndexer(
                        thorClient = thorClient,
                        processor = processor,
                        startBlock = b3trBlock.number,
                        syncLoggerInterval = 1L,
                        bEProperties = businessEventProperties,
                    )

            // Create a coordinator to run the indexer
            SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(b3trBlock))

            val result = indexingResult.captured

            expectThat(result is IndexingResult.Normal).isTrue()

            val normalResult = result as IndexingResult.Normal

            expectThat(normalResult.block.number).isEqualTo(20614874L)
            expect { that(normalResult.events()).hasSize(5) }
            val eventTypes = normalResult.events().map { it.eventType }
            expect {
                that(eventTypes)
                    .isEqualTo(
                        listOf(
                            "Transfer",
                            "VET_TRANSFER",
                            "VET_TRANSFER",
                            "B3TR_ActionReward",
                            "B3TR_ActionReward",
                        )
                    )
            }
            expectThat(indexer.getCurrentBlockNumber()).isEqualTo(b3trBlock.number + 1L)
        }
    }

    @Test
    fun `Process - With DEX TXs`() = runBlocking {
        val dexBlock = BlockFixtures.BLOCK_DEX
        every { processor.getLastSyncedBlock() } returns null

        val indexingResult = slot<IndexingResult>()
        every { processor.rollback(20056658) } returns Unit
        every { processor.process(capture(indexingResult)) } returns Unit

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = dexBlock.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(dexBlock))

        val result = indexingResult.captured
        expectThat(result is IndexingResult.Normal).isTrue()
        val normalResult = result as IndexingResult.Normal

        expect { that(normalResult.events).hasSize(7) }
        val eventTypes = normalResult.events.map { it.eventType }
        expect {
            that(eventTypes)
                .isEqualTo(
                    listOf(
                        "B3TR_ActionReward",
                        "FT_VET_Swap2",
                        "B3TR_ActionReward",
                        "Token_FTSwap",
                        "VET_FT_Swap",
                        "VET_FT_Swap",
                        "FT_VET_Swap",
                    )
                )
        }
    }

    @Test
    fun `Process - With MaaS sale`() = runBlocking {
        val mpSales = BlockFixtures.BLOCK_MP_SALES

        every { processor.getLastSyncedBlock() } returns null

        val indexingResult = slot<IndexingResult>()
        every { processor.rollback(20849467) } returns Unit
        every { processor.process(capture(indexingResult)) } returns Unit

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = mpSales.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(mpSales))

        val result = indexingResult.captured
        expectThat(result is IndexingResult.Normal).isTrue()
        val normalResult = result as IndexingResult.Normal

        expect { that(normalResult.events).hasSize(6) }
        val eventTypes = normalResult.events.map { it.eventType }
        expect {
            that(eventTypes)
                .isEqualTo(
                    listOf(
                        "MAAS_SALE",
                        "WOV_Offer_Accepted_Sale",
                        "WOV_Non_Custodial_Sale",
                        "WOV_Action_Executed_Sale",
                        "WOV_Custodial_WOV_Sale",
                        "WOV_Custodial_VET_Sale",
                    )
                )
        }
    }

    @Test
    fun `Process block - With Stargate TXs via Indexer (Hayabusa)`() = runBlocking {
        val stargateBlocks =
            listOf(
                BlockFixtures.BLOCK_STARGATE_STAKER_DELEGATION,
                BlockFixtures.BLOCK_STARGATE_CLAIM_REWARDS,
                BlockFixtures.BLOCK_STARGATE_MANAGER_ADDED,
                BlockFixtures.BLOCK_STARGATE_MANAGER_REMOVED,
                BlockFixtures.BLOCK_STARGATE_DELEGATION_EXIT_REQUEST,
            )

        val startBlock = BlockFixtures.BLOCK_STARGATE_STAKER_DELEGATION
        every { processor.getLastSyncedBlock() } returns null
        val capturedResults = mutableListOf<IndexingResult>()
        every { processor.rollback(any()) } returns Unit
        every { processor.process(any()) } answers
            {
                val result = firstArg<IndexingResult>()
                capturedResults.add(result)
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = startBlock.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = stargateBlocks)

        expectThat(capturedResults).hasSize(stargateBlocks.size)

        val allEvents =
            capturedResults.filterIsInstance<IndexingResult.Normal>().flatMap { it.events }

        val eventTypes = allEvents.map { it.eventType }
        expect {
            that(eventTypes)
                .isEqualTo(
                    listOf(
                        "STARGATE_BOOST",
                        "STARGATE_DELEGATE_REQUEST",
                        "STARGATE_STAKE",
                        "Transfer",
                        "Transfer",
                        "Transfer",
                        "STARGATE_MANAGER_ADDED",
                        "STARGATE_MANAGER_REMOVED",
                        "STARGATE_DELEGATION_EXIT_REQUEST",
                    )
                )
        }
    }

    @Test
    fun `Process block - With Stargate TXs via Indexer (Legacy)`() = runBlocking {
        val stargateBlocks =
            listOf(
                BlockFixtures.BLOCK_STARGATE_STAKE,
                BlockFixtures.BLOCK_STARGATE_UNSTAKE,
                BlockFixtures.BLOCK_STARGATE_BASE_REWARD,
                BlockFixtures.BLOCK_STARGATE_DELEGATE_REWARD,
                BlockFixtures.BLOCK_STARGATE_STAKE_DELEGATE,
                BlockFixtures.BLOCK_STARGATE_UNDELEGATE,
                BlockFixtures.BLOCK_STARGATE_DELEGATION,
            )

        val startBlock = BlockFixtures.BLOCK_STARGATE_STAKE
        every { processor.getLastSyncedBlock() } returns null
        val capturedResults = mutableListOf<IndexingResult>()
        every { processor.rollback(any()) } returns Unit
        every { processor.process(any()) } answers
            {
                val result = firstArg<IndexingResult>()
                capturedResults.add(result)
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = startBlock.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = stargateBlocks)

        expectThat(capturedResults).hasSize(stargateBlocks.size)

        val allEvents =
            capturedResults.filterIsInstance<IndexingResult.Normal>().flatMap { it.events }

        val eventTypes = allEvents.map { it.eventType }
        expect {
            that(eventTypes)
                .isEqualTo(
                    listOf(
                        "STARGATE_DELEGATE_LEGACY",
                        "STARGATE_STAKE",
                        "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                        "STARGATE_UNSTAKE",
                        "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                        "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                        "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                        "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                        "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                        "STARGATE_DELEGATE_LEGACY",
                        "STARGATE_STAKE",
                        "STARGATE_UNDELEGATE_LEGACY",
                        "Transfer",
                    )
                )
        }
    }
}
