package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_10_SEMI_FUNGIBLE_TOKENS
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_14_VET_TRANSFER
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_17_BATCH_TRANSFERS_1
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_18_BATCH_TRANSFERS_2
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_19_BATCH_TRANSFERS_3
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.utils.AddressUtils
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

    lateinit var transferEventIndexer: TransferEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        transferEventIndexer =
            TransferEventIndexer(
                transferEventRepository,
                mongoTemplate,
                DefaultThorClient("http://localhost:8669"),
                0L,
                1000L
            )
    }

    @Test
    fun `can process semi-fungible transfer events`() {

        val transfersSlot = slot<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processBlock(BLOCK_10_SEMI_FUNGIBLE_TOKENS)

        val transfers = transfersSlot.captured

        expect {
            that(transfers).hasSize(1)
            that(transfers[0].eventType).isEqualTo(TransferEventType.SEMI_FUNGIBLE_TOKEN)
        }
    }

    @Test
    fun `Process block - with no transfer events`() {

        transferEventIndexer.processBlock(BLOCK_3_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `Process block - with transfer events`() {
        val blockNumber = 8L

        val transfersSlot = slot<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processBlock(BLOCK_8_MULTIPLE_CLAUSES)

        val transfers = transfersSlot.captured
        expect { that(transfers).hasSize(10) }
        val transferEvent =
            transfers.first {
                it.id ==
                    DigestUtils.sha1Hex(
                        "0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e-TOPIC-9-0-0"
                    )
            }
        expect {
            that(transferEvent.blockId)
                .isEqualTo("0x00000008de120e47e15edb8d9a23823b198590623c3c9f938c5f623f13e7402e")
            that(transferEvent.blockNumber).isEqualTo(blockNumber)
            that(transferEvent.blockTimestamp).isEqualTo(1680177343)
            that(transferEvent.txId)
                .isEqualTo("0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e")
            that(transferEvent.from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(transferEvent.to).isEqualTo("0xd7f75a0a1287ab2916848909c8531a0ea9412800")
            that(transferEvent.value).isEqualTo(BigInteger.ONE)
            that(transferEvent.tokenAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(transferEvent.topics).hasSize(4).and {
                that(transferEvent.topics[0])
                    .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
                that(transferEvent.topics[1])
                    .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
                that(transferEvent.topics[2])
                    .isEqualTo("0x000000000000000000000000d7f75a0a1287ab2916848909c8531a0ea9412800")
                that(transferEvent.topics[3])
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

        transferEventIndexer.processBlock(BLOCK_14_VET_TRANSFER)

        val transfers = transfersSlot.captured

        expectThat(transfers).hasSize(1)

        val vetTransfer = transfers.first { it.eventType == TransferEventType.VET }

        expect {
            that(vetTransfer.blockId)
                .isEqualTo("0x0000000e554ca3da5e4c5d0294bdea429297f805c1ffc76453cf7d051655bcfb")
            that(vetTransfer.blockNumber).isEqualTo(blockNumber)
            that(vetTransfer.blockTimestamp).isEqualTo(1681734922)
            that(vetTransfer.txId)
                .isEqualTo("0x80f3aadef1e87d54e7e608c64b87df9ab69d631b063cfd60869e7a4574ae2d93")
            that(vetTransfer.from).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(vetTransfer.to).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
            that(vetTransfer.value).isEqualTo(BigInteger.valueOf(10000000))
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

        transferEventIndexer.processBlock(BLOCK_17_BATCH_TRANSFERS_1)
        transferEventIndexer.processBlock(BLOCK_18_BATCH_TRANSFERS_2)
        transferEventIndexer.processBlock(BLOCK_19_BATCH_TRANSFERS_3)

        val transfers = transfersSlot.flatten()

        val vip210Batch =
            transfers.filter {
                it.txId == "0x91d726dc73b0dd2d0b913e814513142f909d4fbf46945bc199b9015f8ffbfcd7" &&
                    it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN
            }
        val erc1155Batch =
            transfers.filter {
                it.txId == "0x601ca7c5430d0b49c266dd349f3a792538805b4fff9b822e66581004bae458e9" &&
                    it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN
            }
        val erc721Batch =
            transfers.filter {
                it.txId == "0xf88748d0803fc08b07a674ffe3d83bb9bc017cc1eb7b2838503ab18dac4b80cc" &&
                    it.eventType == TransferEventType.NFT
            }
        val vip181Batch =
            transfers.filter {
                it.txId == "0xa6ff1dc03cc57bb573d32ae999ccd5e72e8807550b1976d0eb20498b0acfb2bd" &&
                    it.eventType == TransferEventType.NFT
            }
        val vip180 =
            transfers.filter {
                it.txId == "0x5348365b87fbad243b32fdc14ae8981057132ecaabc5a07f53d8c2f05ddda9b1" &&
                    it.eventType == TransferEventType.FUNGIBLE_TOKEN
            }
        val erc20 =
            transfers.filter {
                it.txId == "0xb73d1a49f0c56a15b6b549b271e74aab4e536e060d3c90938aee12f1e41caac4" &&
                    it.eventType == TransferEventType.FUNGIBLE_TOKEN
            }

        expect {
            that(vip210Batch).hasSize(50)
            that(erc1155Batch).hasSize(50)
            that(erc721Batch).hasSize(50)
            that(vip181Batch).hasSize(50)
            that(vip180).hasSize(50)
            that(erc20).hasSize(50)
        }
    }

    @Test
    fun `can pick up semi-fungible single transfer events`() {
        val transfersSlot = mutableListOf<List<IndexedTransferEvent>>()
        every {
            mongoTemplate.insert(capture(transfersSlot), IndexedTransferEvent::class.java)
        } returns mutableListOf()

        transferEventIndexer.processBlock(BLOCK_17_BATCH_TRANSFERS_1)
        transferEventIndexer.processBlock(BLOCK_18_BATCH_TRANSFERS_2)
        transferEventIndexer.processBlock(BLOCK_19_BATCH_TRANSFERS_3)

        val transfers = transfersSlot.flatten().filter { it.from != AddressUtils.ZERO_ADDRESS }

        val vip210Single =
            transfers.find {
                it.from == "0xabef6032b9176c186f6bf984f548bda53349f70a" &&
                    it.to == "0x865306084235bf804c8bba8a8d56890940ca8f0b" &&
                    it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN &&
                    it.tokenAddress == "0x898E50B66fC40Ef5ee80dC0d20b113bEb405434b".lowercase()
            }

        val erc1155Single =
            transfers.find {
                it.from == "0xf370940abdbd2583bc80bfc19d19bc216c88ccf0" &&
                    it.to == "0x99602e4bbc0503b8ff4432bb1857f916c3653b85" &&
                    it.eventType == TransferEventType.SEMI_FUNGIBLE_TOKEN &&
                    it.tokenAddress == "0x7721A4612D055AE028c7c6445a25c75A4715ac96".lowercase()
            }

        expect {
            that(vip210Single).isNotNull()
            that(erc1155Single).isNotNull()
        }
    }
}
