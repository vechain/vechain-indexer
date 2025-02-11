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

        val historyService = HistoryService()

        val abiManager = AbiManager()
        abiManager.loadAbis("test-abis")

        val businessEventManager = BusinessEventManager()
        businessEventManager.loadBusinessEvents("business-events")

        indexer =
            HistoryIndexer(
                historyService = historyService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                historyRepository = historyEventRepository,
                mongoTemplate = mongoTemplate,
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
                    ),
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
                    ),
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
    fun `Process block - With unknown TXs`() {
        val historyEventSlot = slot<List<IndexedHistoryEvent>>()
        every {
            mongoTemplate.insert(capture(historyEventSlot), IndexedHistoryEvent::class.java)
        } returns mutableListOf()

        indexer.processBlock(BlockFixtures.BLOCK_RANDOM_TX)

        val txs = historyEventSlot.captured
        expect { that(txs).hasSize(1) }
        val tx = txs.first()
        expect {
            that(tx.eventName).isEqualTo(HistoryEventName.GENERIC_TX)
            that(tx.tokenId).isEqualTo(null)
            that(tx.value).isEqualTo(null)
            that(tx.from).isEqualTo(null)
            that(tx.to).isEqualTo(null)
            that(tx.contractAddress).isEqualTo(null)
        }
    }
}
