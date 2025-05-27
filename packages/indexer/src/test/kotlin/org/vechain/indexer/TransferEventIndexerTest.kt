package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_BATCH_TRANSFERS
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_MULTIPLE_TXS
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_SEMI_FUNGIBLE_TOKENS
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_VET_TRANSFER_EVENTS
import org.vechain.indexer.fixtures.TransferLogFixtures.LOGS_VET_TRANSFER
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.FileUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

@ExtendWith(MockKExtension::class)
class TransferEventIndexerTest {
    @MockK lateinit var transferEventRepository: TransferEventRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var transferEventIndexer: TransferEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiFileStreams = FileUtils.loadFileStreams("abis")
        val abiManager = AbiManager()
        abiManager.loadAbis(abiFileStreams)

        transferEventIndexer =
            TransferEventIndexer(
                transferEventRepository,
                mongoTemplate,
                DefaultThorClient("http://localhost:8669"),
                abiManager,
                0L,
                1000L,
                1000L,
            )
    }

    @Test
    fun `can process semi-fungible transfer events`() {
        val transfersSlot = slot<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processLogs(LOGS_SEMI_FUNGIBLE_TOKENS, emptyList())

        val transfers = transfersSlot.captured

        expect {
            that(transfers).hasSize(1)
            that(transfers[0].eventType).isEqualTo(TransferEventType.SEMI_FUNGIBLE_TOKEN)
        }
    }

    @Test
    fun `Process block - with no transfer events`() {
        transferEventIndexer.processBlock(BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `Process block - with transfer events`() {
        val blockNumber = 8L

        val transfersSlot = slot<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processLogs(LOGS_MULTIPLE_TXS, emptyList())

        val transfers = transfersSlot.captured
        expect { that(transfers).hasSize(10) }
        expect {
            that(transfers[0].eventType).isEqualTo(TransferEventType.NFT)
            that(transfers[0].blockId)
                .isEqualTo("0x00000008de120e47e15edb8d9a23823b198590623c3c9f938c5f623f13e7402e")
            that(transfers[0].blockNumber).isEqualTo(blockNumber)
            that(transfers[0].blockTimestamp).isEqualTo(1680177343)
            that(transfers[0].txId)
                .isEqualTo("0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e")
            that(transfers[0].from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(transfers[0].to).isEqualTo("0xd7f75a0a1287ab2916848909c8531a0ea9412800")
            that(transfers[0].value).isEqualTo("1")
            that(transfers[0].tokenAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(transfers[0].topics).hasSize(4).and {
                that(transfers[0].topics[0])
                    .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
                that(transfers[0].topics[1])
                    .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
                that(transfers[0].topics[2])
                    .isEqualTo("0x000000000000000000000000d7f75a0a1287ab2916848909c8531a0ea9412800")
                that(transfers[0].topics[3])
                    .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
            }
        }
    }

    @Test
    fun `can pick up VET transfer`() {
        val blockNumber = 14L

        val transfersSlot = slot<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processLogs(LOGS_VET_TRANSFER_EVENTS, LOGS_VET_TRANSFER)

        val transfers = transfersSlot.captured

        expectThat(transfers).hasSize(1)

        val vetTransfer = transfers.first { it.eventType == TransferEventType.VET }

        expect {
            that(vetTransfer.blockId)
                .isEqualTo("0x0000000e554ca3da5e4c5d0294bdea429297f805c1ffc76453cf7d051655bcfb")
            that(vetTransfer.blockNumber).isEqualTo(blockNumber)
            that(vetTransfer.blockTimestamp).isEqualTo(1681734922)
            that(vetTransfer.txId)
                .isEqualTo("0x56410f73d1f3ed0bc2d8fb6f6fd2b7807429ec6b02f8276982e8c7736f3e9ff2")
            that(vetTransfer.from).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(vetTransfer.to).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
            that(vetTransfer.value).isEqualTo("10000000")
            that(vetTransfer.tokenAddress).isNull()
            that(vetTransfer.topics).hasSize(0)
        }
    }

    @Test
    fun `can pick up batch mint events`() {
        val transfersSlot = mutableListOf<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processLogs(LOGS_BATCH_TRANSFERS, emptyList())

        val transfers = transfersSlot.flatten()

        val erc1155 = transfers.filter { it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN }

        val nft = transfers.filter { it.eventType == TransferEventType.NFT }

        val fungible = transfers.filter { it.eventType == TransferEventType.FUNGIBLE_TOKEN }

        expect {
            that(erc1155).hasSize(16)
            that(nft).hasSize(1)
            that(fungible).hasSize(11)
        }
    }

    @Test
    fun `can pick up semi-fungible single transfer events`() {
        val transfersSlot = mutableListOf<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processLogs(LOGS_BATCH_TRANSFERS, emptyList())

        val transfers = transfersSlot.flatten().filter { it.from != Address.ZERO_ADDRESS }

        val erc1155Single =
            transfers.find {
                it.from == "0xf370940abdbd2583bc80bfc19d19bc216c88ccf0" &&
                    it.to == "0x99602e4bbc0503b8ff4432bb1857f916c3653b85" &&
                    it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN &&
                    it.tokenAddress == "0x7721A4612D055AE028c7c6445a25c75A4715ac96".lowercase()
            }

        expect { that(erc1155Single).isNotNull() }
    }
}
