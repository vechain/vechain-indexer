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
                        HistoryEventName.B3TR_ACTION.name,
                        HistoryEventName.SWAP_FT_TO_VET.name,
                        HistoryEventName.B3TR_ACTION.name,
                        HistoryEventName.SWAP_FT_TO_FT.name,
                        HistoryEventName.SWAP_VET_TO_FT.name,
                        HistoryEventName.SWAP_VET_TO_FT.name,
                        HistoryEventName.SWAP_FT_TO_VET.name,
                    )
                )
        }
    }

    @Test
    fun `Process - With MaaS sale`() = runBlocking {
        val mpSales = BlockFixtures.BLOCK_MP_SALES

        every { processor.getLastSyncedBlock() } returns null

        val indexingResult = slot<IndexingResult>()
        every { processor.rollback(20849466) } returns Unit
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
                        HistoryEventName.NFT_SALE.name,
                        HistoryEventName.NFT_SALE.name,
                        HistoryEventName.NFT_SALE.name,
                        HistoryEventName.NFT_SALE.name,
                        HistoryEventName.NFT_SALE.name,
                        HistoryEventName.NFT_SALE.name,
                    )
                )
        }
    }

    @Test
    fun `Process - With BatchTransfer TXs`() = runBlocking {
        val blockSF = BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS
        val blockSF2 = BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS_2

        every { processor.getLastSyncedBlock() } returns null

        val insertedEvents = mutableListOf<List<IndexedHistoryEvent>>()

        every { processor.rollback(10) } returns Unit
        every { repository.saveAll(any<List<IndexedHistoryEvent>>()) } answers
            {
                val events = firstArg<List<IndexedHistoryEvent>>()
                insertedEvents.add(events)
                mutableListOf()
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = blockSF.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(blockSF, blockSF2))

        // Flatten all captured events
        val allTxs = insertedEvents.flatten()
        print(allTxs)

        expect { that(allTxs).hasSize(5) }

        // First block assertions (should have 1)
        val first = insertedEvents.first()
        expect { that(first).hasSize(1) }

        val tx1 = first.first()
        expect {
            that(tx1.eventName).isEqualTo(HistoryEventName.TRANSFER_SF)
            that(tx1.tokenId).isEqualTo("2")
            that(tx1.value).isEqualTo("963")
            that(tx1.from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(tx1.to).isEqualTo("0x361277d1b27504f36a3b33d3a52d1f8270331b8c")
            that(tx1.contractAddress).isEqualTo("0xf5cfa7d8f766c904ef2b7abd229d6eea22fca665")
        }

        // Second block assertions (should have 4)
        val second = insertedEvents[1]
        expect { that(second).hasSize(4) }
        expect {
            that(second.map { it.tokenId }).isEqualTo(listOf("0", "1", "2", "3"))
            that(second.map { it.value }).isEqualTo(listOf("0", "10", "5", "35"))
            that(second.map { it.eventName }.distinct())
                .isEqualTo(listOf(HistoryEventName.TRANSFER_SF))
        }
    }

    @Test
    fun `Process block - With Stargate TXs via Indexer`() = runBlocking {
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
        every { processor.getLastSyncedBlock() } returns null

        val insertedEvents = mutableListOf<List<IndexedHistoryEvent>>()
        every { processor.rollback(0) } returns Unit
        every { repository.saveAll(any<List<IndexedHistoryEvent>>()) } answers
            {
                val events = firstArg<List<IndexedHistoryEvent>>()
                insertedEvents.add(events)
                mutableListOf()
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = 1L,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = stargateBlocks)

        val allEvents = insertedEvents.flatten()

        expect { that(allEvents).hasSize(12) }

        fun assertTx(
            idx: Int,
            eventName: HistoryEventName,
            tokenId: String,
            value: String? = null,
            levelId: String? = null,
            owner: String? = null,
            migrated: Boolean? = null,
            autorenew: Boolean? = null,
        ) {
            val tx = allEvents[idx]
            expect {
                that(tx.eventName).isEqualTo(eventName)
                that(tx.tokenId).isEqualTo(tokenId)
                value?.let { that(tx.value).isEqualTo(it) }
                levelId?.let { that(tx.levelId).isEqualTo(it) }
                owner?.let { that(tx.owner).isEqualTo(it) }
                migrated?.let { that(tx.migrated).isEqualTo(it) }
                autorenew?.let { that(tx.autorenew).isEqualTo(it) }
            }
        }

        assertTx(
            idx = 0,
            eventName = HistoryEventName.STARGATE_DELEGATE_ONLY,
            tokenId = "16",
            autorenew = true,
        )

        assertTx(
            idx = 1,
            eventName = HistoryEventName.STARGATE_STAKE,
            tokenId = "16",
            value = "100000000000000000000",
            levelId = "1",
            owner = "0x0f872421dc479f3c11edd89512731814d0598db5",
        )

        assertTx(
            idx = 2,
            eventName = HistoryEventName.STARGATE_CLAIM_REWARDS_BASE,
            tokenId = "9",
            value = "8750000000000",
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        )

        assertTx(
            idx = 3,
            eventName = HistoryEventName.STARGATE_UNSTAKE,
            tokenId = "9",
            value = "5000000000000000000",
            levelId = "9",
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        )

        assertTx(
            idx = 4,
            eventName = HistoryEventName.STARGATE_CLAIM_REWARDS_BASE,
            tokenId = "8",
            value = "2177150000000000",
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        )

        assertTx(
            idx = 5,
            eventName = HistoryEventName.STARGATE_CLAIM_REWARDS_BASE,
            tokenId = "10",
            value = "2176250000000000",
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        )

        assertTx(
            idx = 8,
            eventName = HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
            tokenId = "4",
            value = "260192920440000000000",
            owner = "0x0f872421dc479f3c11edd89512731814d0598db5",
        )

        assertTx(
            idx = 9,
            eventName = HistoryEventName.STARGATE_DELEGATE_ONLY,
            tokenId = "10",
            autorenew = true,
        )

        assertTx(
            idx = 10,
            eventName = HistoryEventName.STARGATE_STAKE,
            tokenId = "10",
            value = "1000000000000000000",
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            levelId = "8",
            migrated = false,
        )

        assertTx(idx = 11, eventName = HistoryEventName.STARGATE_UNDELEGATE, tokenId = "16")
    }

    @Test
    fun `Process block - Stargate VTHO refund`() = runBlocking {
        val block = BlockFixtures.BLOCK_STARGATE_VTHO_REFUND

        every { processor.getLastSyncedBlock() } returns null

        val indexingResult = slot<IndexingResult>()
        every { processor.rollback(7) } returns Unit
        every { processor.process(capture(indexingResult)) } returns Unit

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    startBlock = block.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        // Create a coordinator to run the indexer
        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(block))

        val result = indexingResult.captured
        expectThat(result is IndexingResult.Normal).isTrue()
        val normalResult = result as IndexingResult.Normal

        expect { that(result.events).hasSize(6) }
        val eventTypes = result.events().map { it.eventType }
        expect {
            that(eventTypes)
                .isEqualTo(
                    listOf(
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE.name,
                    )
                )
        }
    }
}
