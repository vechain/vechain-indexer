package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.NFTRepo
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {
    
    @MockK
    lateinit var nftRepo: NFTRepo

    lateinit var nftEventIndexer: NFTEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftEventIndexer = NFTEventIndexer(nftRepo, "http://localhost:8669")
    }

    @Test
    fun `Process block - with NFT transfer events`() {
        val blockNumber = 8L

        val nftsSlot = slot<List<IndexedNFT>>()
        every { nftRepo.saveAll(capture(nftsSlot)) } returns mutableListOf()

        nftEventIndexer.processBlock(BLOCK_8_MULTIPLE_CLAUSES)

        val nftEvents = nftsSlot.captured
        expectThat(nftEvents).hasSize(10)

        val nftEvent = nftEvents.first()
        expect {
            that(nftEvent.id).isEqualTo(DigestUtils.sha1Hex("0x1f734d58eb6a349f038c28f112478bf90981c87e-1"))
            that(nftEvent.tokenId).isEqualTo("1")
            that(nftEvent.contractAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(nftEvent.owner).isEqualTo("0x361277d1b27504f36a3b33d3a52d1f8270331b8c")
            that(nftEvent.txId).isEqualTo("0x039d5f9bd7ffadd11a4f5888e88f459b7ff349c8c06e820f3102fa0779867ac6")
            that(nftEvent.blockNumber).isEqualTo(blockNumber)
            that(nftEvent.blockTimestamp).isEqualTo(1680177343)
        }
    }

    @Test
    fun `Process block - with no NFT transfer events`() {

        nftEventIndexer.processBlock(BLOCK_3_NO_CLAUSES)

        verify { nftRepo wasNot Called }
    }
}