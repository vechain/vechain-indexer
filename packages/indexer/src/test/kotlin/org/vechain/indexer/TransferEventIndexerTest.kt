package org.vechain.indexer

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan

@ExtendWith(MockKExtension::class)
class TransferEventIndexerTest {

    @MockK
    lateinit var thorService: ThorService

    @MockK
    lateinit var transferEventRepo: TransferEventRepo

    @MockK
    lateinit var nftRepo: NFTRepo

    @InjectMockKs
    lateinit var transferEventIndexer: TransferEventIndexer

    @Test
    fun `Starting block is transfer block - when transfer block is lower`() {
        val transferEventsStartingBlock = 9L
        val nftsStartingBlock = 10L

        every { transferEventRepo.getMaxBlockNumber() } returns listOf(buildTransferEvent(transferEventsStartingBlock))
        every { nftRepo.getMaxBlockNumber() } returns listOf(buildNft(nftsStartingBlock))


        val indexerStartingBlock = transferEventIndexer.getStartingBlock()


        expectThat(transferEventsStartingBlock).isLessThan(nftsStartingBlock)
        expectThat(indexerStartingBlock).isEqualTo(transferEventsStartingBlock)
    }

    @Test
    fun `Starting block is nft block - when nft block is lower`() {
        val transferEventsStartingBlock = 10L
        val nftsStartingBlock = 9L

        every { transferEventRepo.getMaxBlockNumber() } returns listOf(buildTransferEvent(transferEventsStartingBlock))
        every { nftRepo.getMaxBlockNumber() } returns listOf(buildNft(nftsStartingBlock))


        val indexerStartingBlock = transferEventIndexer.getStartingBlock()


        expectThat(nftsStartingBlock).isLessThan(transferEventsStartingBlock)
        expectThat(indexerStartingBlock).isEqualTo(nftsStartingBlock)
    }

    @Test
    fun `Process block - with no transfer events`() {
        val blockNumber = 3L
        every { thorService.getBlock(blockNumber) } returns BLOCK_3_NO_CLAUSES


        transferEventIndexer.processBlock(blockNumber)


        verify { transferEventRepo wasNot Called }
    }

    @Test
    fun `Process block - with transfer events`() {
        val blockNumber = 8L
        every { thorService.getBlock(blockNumber) } returns BLOCK_8_MULTIPLE_CLAUSES

        val transfersSlot = slot<List<TransferEvent>>()
        every { transferEventRepo.saveAll(capture(transfersSlot)) } returns mutableListOf()


        transferEventIndexer.processBlock(blockNumber)


        val transfers = transfersSlot.captured
        expect {
            that(transfers).hasSize(10)
        }
        val transferEvent =
            transfers.first { it.id == "0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e-0" }
        expect {
            that(transferEvent.blockId).isEqualTo("0x00000008de120e47e15edb8d9a23823b198590623c3c9f938c5f623f13e7402e")
            that(transferEvent.blockNumber).isEqualTo(blockNumber)
            that(transferEvent.txId).isEqualTo("0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e")
            that(transferEvent.clauseIndex).isEqualTo(0)
            that(transferEvent.from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(transferEvent.to).isEqualTo("0xd7f75a0a1287ab2916848909c8531a0ea9412800")
            that(transferEvent.value).isEqualTo("0x")
            that(transferEvent.tokenAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(transferEvent.topics).hasSize(4).and {
                that(transferEvent.topics[0]).isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
                that(transferEvent.topics[1]).isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
                that(transferEvent.topics[2]).isEqualTo("0x000000000000000000000000d7f75a0a1287ab2916848909c8531a0ea9412800")
                that(transferEvent.topics[3]).isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
            }
        }
    }

    private fun buildTransferEvent(blockNumber: Long) = TransferEvent(
        id = "id",
        blockId = "blockId",
        blockNumber = blockNumber,
        txId = "txId",
        clauseIndex = 1,
        from = "from",
        to = "to",
        value = "value",
        tokenAddress = "address",
        topics = emptyList(),
    )

    private fun buildNft(blockNumber: Long) = NFT(
        id = "id",
        tokenId = null,
        contractAddress = "address",
        owner = "owner",
        txId = "txId",
        blockNumber = blockNumber
    )
}