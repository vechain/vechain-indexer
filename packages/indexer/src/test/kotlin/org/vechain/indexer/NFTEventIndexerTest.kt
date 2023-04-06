package org.vechain.indexer

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.model.NFT
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.math.BigInteger

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {

    @MockK
    lateinit var thorService: ThorService

    @MockK
    lateinit var nftRepo: NFTRepo

    @InjectMockKs
    lateinit var nftEventIndexer: NFTEventIndexer

    @Test
    fun `Process block - with NFT transfer events`() {
        val blockNumber = 8L
        every { thorService.getBlock(blockNumber) } returns BLOCK_8_MULTIPLE_CLAUSES

        val nftsSlot = slot<List<NFT>>()
        every { nftRepo.saveAll(capture(nftsSlot)) } returns mutableListOf()


        nftEventIndexer.processBlock(blockNumber)


        val nftEvents = nftsSlot.captured
        expectThat(nftEvents).hasSize(10)

        val nftEvent = nftEvents.first()
        expect {
            that(nftEvent.id).isEqualTo(DigestUtils.sha1Hex("0x1f734d58eb6a349f038c28f112478bf90981c87e-0"))
            that(nftEvent.tokenId).isEqualTo(BigInteger.ZERO)
            that(nftEvent.contractAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(nftEvent.owner).isEqualTo("0xd7f75a0a1287ab2916848909c8531a0ea9412800")
            that(nftEvent.txId).isEqualTo("0xe896f18857b416ea5553be739848911ee75593012f4853e775f39bef10eeae2e")
            that(nftEvent.blockNumber).isEqualTo(blockNumber)
        }
    }

    @Test
    fun `Process block - with no NFT transfer events`() {
        val blockNumber = 3L
        every { thorService.getBlock(blockNumber) } returns BLOCK_3_NO_CLAUSES


        nftEventIndexer.processBlock(blockNumber)


        verify { nftRepo wasNot Called }
    }
}