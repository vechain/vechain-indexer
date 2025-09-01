package org.vechain.indexer.history

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.CombinedEventProcessor
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.mocks.TestableBlockIndexer
import org.vechain.indexer.thor.client.MockThorClient
import strikt.api.expect
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
class HistoryIndexerTest {
    @MockK lateinit var mongoTemplate: MongoTemplate

    private val repository = mockk<HistoryRepository>(relaxed = true)

    lateinit var processor: HistoryProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val historyService = HistoryService(mongoTemplate = mongoTemplate)

        processor = HistoryProcessor(repository, historyService)
    }

    @Test
    fun `Process - With B3TR Action TXs`() = runBlocking {
        val b3trBlock = BlockFixtures.BLOCK_B3TR_ACTION

        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        val eventProcessor = buildEventProcessor()

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient = MockThorClient(mapOf(b3trBlock.number to b3trBlock)),
                processor = processor,
                eventProcessor = eventProcessor,
                startBlock = b3trBlock.number,
            )

        indexer.start(1)

        val txs = historyEventSlot.captured

        txs.forEach { tx -> println(tx.eventName) }
        expect { that(txs).hasSize(5) }
        val eventNames = txs.map { it.eventName }
        expect {
            that(eventNames)
                .isEqualTo(
                    listOf(
                        HistoryEventName.TRANSFER_FT,
                        HistoryEventName.TRANSFER_VET,
                        HistoryEventName.TRANSFER_VET,
                        HistoryEventName.B3TR_ACTION,
                        HistoryEventName.B3TR_ACTION,
                    )
                )
        }
    }

    @Test
    fun `Process - With DEX TXs`() = runBlocking {
        val dexBlock = BlockFixtures.BLOCK_DEX

        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient = MockThorClient(mapOf(dexBlock.number to dexBlock)),
                processor = processor,
                eventProcessor = buildEventProcessor(),
                startBlock = dexBlock.number,
            )

        indexer.start(1)

        val txs = historyEventSlot.captured

        txs.forEach { tx -> println(tx.eventName) }
        expect { that(txs).hasSize(7) }
        val eventNames = txs.map { it.eventName }
        expect {
            that(eventNames)
                .isEqualTo(
                    listOf(
                        HistoryEventName.B3TR_ACTION,
                        HistoryEventName.SWAP_FT_TO_VET,
                        HistoryEventName.B3TR_ACTION,
                        HistoryEventName.SWAP_FT_TO_FT,
                        HistoryEventName.SWAP_VET_TO_FT,
                        HistoryEventName.SWAP_VET_TO_FT,
                        HistoryEventName.SWAP_FT_TO_VET,
                    )
                )
        }
    }

    @Test
    fun `Process - With MaaS sale`() = runBlocking {
        val mpSales = BlockFixtures.BLOCK_MP_SALES

        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient = MockThorClient(mapOf(mpSales.number to mpSales)),
                processor = processor,
                eventProcessor = buildEventProcessor(),
                startBlock = mpSales.number,
            )

        indexer.start(1)

        val txs = historyEventSlot.captured

        txs.forEach { tx -> println(tx.eventName) }
        expect { that(txs).hasSize(6) }
        val eventNames = txs.map { it.eventName }
        expect {
            that(eventNames)
                .isEqualTo(
                    listOf(
                        HistoryEventName.NFT_SALE,
                        HistoryEventName.NFT_SALE,
                        HistoryEventName.NFT_SALE,
                        HistoryEventName.NFT_SALE,
                        HistoryEventName.NFT_SALE,
                        HistoryEventName.NFT_SALE,
                    )
                )
        }
    }

    @Test
    fun `Process - With BatchTransfer TXs`() = runBlocking {
        val blockSF = BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS
        val blockSF2 = BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS_2

        val insertedEvents = mutableListOf<List<IndexedHistoryEvent>>()

        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(any<List<IndexedHistoryEvent>>(), IndexedHistoryEvent::class.java)
        } answers
            {
                val events = firstArg<List<IndexedHistoryEvent>>()
                insertedEvents.add(events)
                mutableListOf()
            }

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient =
                    MockThorClient(mapOf(blockSF.number to blockSF, blockSF2.number to blockSF2)),
                processor = processor,
                eventProcessor = buildEventProcessor(),
                startBlock = blockSF.number,
            )

        indexer.start(2)

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

        val insertedEvents = mutableListOf<List<IndexedHistoryEvent>>()
        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(any<List<IndexedHistoryEvent>>(), IndexedHistoryEvent::class.java)
        } answers
            {
                val events = firstArg<List<IndexedHistoryEvent>>()
                insertedEvents.add(events)
                mutableListOf()
            }

        val blockMap = stargateBlocks.mapIndexed { i, block -> (i + 1L) to block }.toMap()

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient = MockThorClient(blockMap),
                processor = processor,
                eventProcessor = buildEventProcessor(),
                startBlock = 1L,
            )

        indexer.start(7)

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

        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every { repository.getLatestRecord() } returns null
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        val indexer =
            TestableBlockIndexer(
                name = "TestHistoryIndexer",
                thorClient = MockThorClient(mapOf(block.number to block)),
                processor = processor,
                eventProcessor = buildEventProcessor(),
                startBlock = block.number,
            )

        indexer.start(1)

        val txs = historyEventSlot.captured

        txs.forEach { tx -> println(tx.eventName) }
        expect { that(txs).hasSize(6) }
        val eventNames = txs.map { it.eventName }
        expect {
            that(eventNames)
                .isEqualTo(
                    listOf(
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                        HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE,
                    )
                )
        }
    }

    fun buildEventProcessor(): CombinedEventProcessor =
        CombinedEventProcessor.create(
            abiBasePath = "abis",
            abiEventNames = listOf("Transfer", "TransferSingle", "TransferBatch"),
            abiContracts = emptyList(),
            includeVetTransfers = true,
            businessEventPath = "business-events",
            businessEventAbiBasePath = "abis",
            businessEventContracts = emptyList(),
            businessEventNames = emptyList(),
            substitutionParams =
                mapOf(
                    "B3TR_CONTRACT" to "0x5ef79995fe8a89e0812330e4378eb2660cede699",
                    "VOT3_CONTRACT" to "0x76ca782b59c74d088c7d2cce2f211bc00836c602",
                    "B3TR_GOVERNOR_CONTRACT" to "0x1c65c25fabe2fc1bcb82f253fa0c916a322f777c",
                    "GM_NFT_CONTRACT" to "0x93b8cd34a7fc4f53271b9011161f7a2b5fea9d1f",
                    "X_ALLOC_VOTING_CONTRACT" to "0x89a00bb0947a30ff95beef77a66aede3842fe5b7",
                    "X2EARN_REWARDS_POOL_CONTRACT" to "0x6bee7ddab6c99d5b2af0554eaea484ce18f52631",
                    "VOTER_REWARDS_CONTRACT" to "0x838a33af756a6366f93e201423e1425f67ec0fa7",
                    "TREASURY_CONTRACT" to "0xd5903bcc66e439c753e525f8af2fec7be2429593",
                    "STARGATE_DELEGATION_CONTRACT" to "0x7240e3bc0d26431512d5b67dbd26d199205bffe8",
                    "STARGATE_NFT_CONTRACT" to "0x1ec1d168574603ec35b9d229843b7c2b44bcb770",
                    "VEVOTE_CONTRACT" to "0x1c65c25fabe2fc1bcb82f253fa0c916a322f777c",
                ),
        )
}
