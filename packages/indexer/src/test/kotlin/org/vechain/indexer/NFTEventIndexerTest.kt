package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NFT_MINT_2
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.BlockUtils.getNftTransferEventsFromTopics
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {

    @MockK lateinit var nftRepository: NFTRepository

    @MockK lateinit var nftService: NFTService

    private lateinit var indexer: NFTEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        indexer =
            NFTEventIndexer(
                nftService = nftService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                nftRepository = nftRepository,
                startBlock = 0L,
                syncLogInterval = 1000L,
                prunerEnabled = true,
                prunerInterval = 1000L
            )
    }

    @Test
    fun `findExisting - empty list`() {
        val data = emptyList<IndexedTransferEvent>()
        every { nftService.getExisting(any()) } returns emptyList()

        val result = indexer.findExisting(data)

        expect { that(result).isEmpty() }

        verify(exactly = 1) { nftService.getExisting(emptyList()) }
    }

    @Test
    fun `findExisting - non-empty list`() {
        val data = getNftTransferEventsFromTopics(BLOCK_NFT_MINT_2)

        every { nftService.getExisting(any()) } returns emptyList()

        indexer.findExisting(data)

        verify(exactly = 1) { nftService.getExisting(data) }
    }

    @Test
    fun `parseRecords - empty list`() {
        val data = emptyList<IndexedTransferEvent>()
        val existing = emptyList<IndexedNFT>()

        val result = indexer.parseRecords(BLOCK_NFT_MINT_2, data, existing)

        expect { that(result).isEmpty() }
    }

    @Test
    fun `parseRecords - non-empty list`() {
        val data = getNftTransferEventsFromTopics(BLOCK_NFT_MINT_2)
        val existing = emptyList<IndexedNFT>()

        val result = indexer.parseRecords(BLOCK_NFT_MINT_2, data, existing)

        expectThat(result.size).isEqualTo(2)
        expectThat(result[0].version).isEqualTo(1)
        expectThat(result[0].id).isEqualTo("93bed94c547a207d9a8ff1daee175feded00304b")
        expectThat(result[0].owner).isEqualTo(data[0].to)
        expectThat(result[0].contractAddress).isEqualTo(data[0].tokenAddress)
        expectThat(result[0].blockId).isEqualTo(BLOCK_NFT_MINT_2.id)
        expectThat(result[0].blockNumber).isEqualTo(BLOCK_NFT_MINT_2.number)
        expectThat(result[0].blockTimestamp).isEqualTo(BLOCK_NFT_MINT_2.timestamp)
        expectThat(result[0].txId).isEqualTo(data[0].txId)
        expectThat(result[0].tokenId).isEqualTo("0")

        expectThat(result[1].version).isEqualTo(1)
        expectThat(result[1].id).isEqualTo("7f1481b3f7bb8d3aefd5523e4035869da1a973af")
        expectThat(result[1].owner).isEqualTo(data[1].to)
        expectThat(result[1].contractAddress).isEqualTo(data[1].tokenAddress)
        expectThat(result[1].blockId).isEqualTo(BLOCK_NFT_MINT_2.id)
        expectThat(result[1].blockNumber).isEqualTo(BLOCK_NFT_MINT_2.number)
        expectThat(result[1].blockTimestamp).isEqualTo(BLOCK_NFT_MINT_2.timestamp)
        expectThat(result[1].txId).isEqualTo(data[1].txId)
        expectThat(result[1].tokenId).isEqualTo("1")
    }

    @Test
    fun `parseRecords - existing record - version should increase and existing be archived`() {
        val data = getNftTransferEventsFromTopics(BLOCK_NFT_MINT_2)
        val existing =
            listOf(
                IndexedNFT(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 1,
                    owner = "0x0001",
                    contractAddress = data[0].tokenAddress!!,
                    tokenId = "0",
                    txId = "0x0002",
                    blockId = "0x0003",
                    blockNumber = 1L,
                    blockTimestamp = 3,
                )
            )

        val result = indexer.parseRecords(BLOCK_NFT_MINT_2, data, existing)

        expectThat(result.size).isEqualTo(2)
        expectThat(result[0].version).isEqualTo(2)
        expectThat(result[0].id).isEqualTo("93bed94c547a207d9a8ff1daee175feded00304b")
        expectThat(result[0].owner).isEqualTo(data[0].to)
        expectThat(result[0].contractAddress).isEqualTo(data[0].tokenAddress)
        expectThat(result[0].blockId).isEqualTo(BLOCK_NFT_MINT_2.id)
        expectThat(result[0].blockNumber).isEqualTo(BLOCK_NFT_MINT_2.number)
        expectThat(result[0].blockTimestamp).isEqualTo(BLOCK_NFT_MINT_2.timestamp)
        expectThat(result[0].txId).isEqualTo(data[0].txId)
        expectThat(result[0].tokenId).isEqualTo("0")

        expectThat(result[1].version).isEqualTo(1)
        expectThat(result[1].id).isEqualTo("7f1481b3f7bb8d3aefd5523e4035869da1a973af")
        expectThat(result[1].owner).isEqualTo(data[1].to)
        expectThat(result[1].contractAddress).isEqualTo(data[1].tokenAddress)
        expectThat(result[1].blockId).isEqualTo(BLOCK_NFT_MINT_2.id)
        expectThat(result[1].blockNumber).isEqualTo(BLOCK_NFT_MINT_2.number)
        expectThat(result[1].blockTimestamp).isEqualTo(BLOCK_NFT_MINT_2.timestamp)
        expectThat(result[1].txId).isEqualTo(data[1].txId)
        expectThat(result[1].tokenId).isEqualTo("1")
    }
}
