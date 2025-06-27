package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.fixtures.FileFixtures.abiFiles
import org.vechain.indexer.fixtures.FileFixtures.businessEventFiles
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.repository.HistoryEventRepository
import org.vechain.indexer.service.HistoryService
import org.vechain.indexer.thor.client.DefaultThorClient
import strikt.api.expect
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class HistoryIndexerTest {
    @MockK lateinit var historyEventRepository: HistoryEventRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var indexer: HistoryIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val historyService =
            HistoryService(
                historyRepository = historyEventRepository,
                mongoTemplate = mongoTemplate,
            )

        val abiManager = AbiManager(abiFiles)
        val businessEventManager = BusinessEventManager(businessEventFiles, BUSINESS_EVENT_PARAMS)

        indexer =
            HistoryIndexer(
                historyService = historyService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                historyRepository = historyEventRepository,
                startBlock = 0L,
                syncLogInterval = 1000L,
                abiManager = abiManager,
                businessEventManager = businessEventManager,
            )
    }

    @Test
    fun `Process block - With no transactions`() {
        indexer.processBlock(BlockFixtures.BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `Process block - With regular transactions (Labelled TXs)`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_B3TR_ACTION)

        val txs = historyEventSlot.captured
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
    fun `Process block - With dex transactions (Labelled TXs)`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_DEX)

        val txs = historyEventSlot.captured
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
    fun `Process block - With MaaS sale`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_MP_SALES)

        val txs = historyEventSlot.captured
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
    fun `Process block - With BatchTransfer TXs (SF TXs)`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS)

        val txs1 = historyEventSlot.captured
        expect { that(txs1).hasSize(1) }
        val tx1 = txs1.first()
        expect {
            that(tx1.eventName).isEqualTo(HistoryEventName.TRANSFER_SF)
            that(tx1.tokenId).isEqualTo("2")
            that(tx1.value).isEqualTo("963")
            that(tx1.from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(tx1.to).isEqualTo("0x361277d1b27504f36a3b33d3a52d1f8270331b8c")
            that(tx1.contractAddress).isEqualTo("0xf5cfa7d8f766c904ef2b7abd229d6eea22fca665")
        }

        indexer.processBlock(BlockFixtures.BLOCK_SEMI_FUNGIBLE_TOKENS_2)
        val txs2 = historyEventSlot.captured
        expect { that(txs2).hasSize(4) }
        val eventNames = txs2.map { it.eventName }
        expect {
            that(eventNames)
                .isEqualTo(
                    listOf(
                        HistoryEventName.TRANSFER_SF,
                        HistoryEventName.TRANSFER_SF,
                        HistoryEventName.TRANSFER_SF,
                        HistoryEventName.TRANSFER_SF,
                    )
                )
            that(txs2[0].tokenId).isEqualTo("0")
            that(txs2[1].tokenId).isEqualTo("1")
            that(txs2[2].tokenId).isEqualTo("2")
            that(txs2[3].tokenId).isEqualTo("3")

            that(txs2[0].value).isEqualTo("0")
            that(txs2[1].value).isEqualTo("10")
            that(txs2[2].value).isEqualTo("5")
            that(txs2[3].value).isEqualTo("35")
        }
    }

    @Test
    fun `Process block - With Stargate TXs`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_STAKE)

        val txs1 = historyEventSlot.captured
        expect { that(txs1).hasSize(1) }
        val tx1 = txs1.first()
        expect {
            that(tx1.eventName).isEqualTo(HistoryEventName.STARGATE_DELEGATE)
            that(tx1.tokenId).isEqualTo("16")
            that(tx1.value).isEqualTo("100000000000000000000")
            that(tx1.levelId).isEqualTo("1")
            that(tx1.owner).isEqualTo("0x0f872421dc479f3c11edd89512731814d0598db5")
            that(tx1.migrated).isEqualTo(false)
        }

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_UNSTAKE)

        val txs2 = historyEventSlot.captured

        expect { that(txs2).hasSize(1) }
        val tx2 = txs2.first()
        expect {
            that(tx2.eventName).isEqualTo(HistoryEventName.STARGATE_UNSTAKE)
            that(tx2.tokenId).isEqualTo("9")
            that(tx2.value).isEqualTo("5000000000000000000")
            that(tx2.levelId).isEqualTo("9")
            that(tx2.owner).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        }

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_BASE_REWARD)

        val txs3 = historyEventSlot.captured
        expect { that(txs3).hasSize(4) }
        val tx3 = txs3.first()
        expect {
            that(tx3.eventName).isEqualTo(HistoryEventName.STARGATE_CLAIM_REWARDS_BASE)
            that(tx3.tokenId).isEqualTo("8")
            that(tx3.value).isEqualTo("2177150000000000")
            that(tx3.owner).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        }

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_DELEGATE_REWARD)

        val txs4 = historyEventSlot.captured
        expect { that(txs4).hasSize(1) }
        val tx4 = txs4.first()
        expect {
            that(tx4.eventName).isEqualTo(HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE)
            that(tx4.tokenId).isEqualTo("10")
            that(tx4.value).isEqualTo("34818662265000000000")
            that(tx4.owner).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        }

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_STAKE_DELEGATE)

        val txs5 = historyEventSlot.captured
        expect { that(txs5).hasSize(1) }
        val tx5 = txs5.first()
        expect {
            that(tx5.eventName).isEqualTo(HistoryEventName.STARGATE_DELEGATE)
            that(tx5.tokenId).isEqualTo("10")
            that(tx5.value).isEqualTo("1000000000000000000")
            that(tx5.owner).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(tx5.levelId).isEqualTo("8")
            that(tx5.autorenew).isEqualTo(true)
            that(tx5.migrated).isEqualTo(false)
        }

        indexer.processBlock(BlockFixtures.BLOCK_STARGATE_UNDELEGATE)

        val txs6 = historyEventSlot.captured
        expect { that(txs6).hasSize(1) }
        val tx6 = txs6.first()
        expect {
            that(tx6.eventName).isEqualTo(HistoryEventName.STARGATE_UNDELEGATE)
            that(tx6.tokenId).isEqualTo("16")
        }
    }

    @Test
    fun `Process block - With unknown TXs`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_RANDOM_TX)

        val txs = historyEventSlot.captured
        expect { that(txs).hasSize(8) }
        val tx = txs.first()
        expect {
            that(tx.eventName).isEqualTo(HistoryEventName.UNKNOWN_TX)
            that(tx.tokenId).isEqualTo(null)
            that(tx.value).isEqualTo(null)
            that(tx.from).isEqualTo(null)
            that(tx.to).isEqualTo(null)
            that(tx.contractAddress).isEqualTo(null)
        }
    }
}
