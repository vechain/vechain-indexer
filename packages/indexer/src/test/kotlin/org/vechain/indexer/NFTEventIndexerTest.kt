package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.fixtures.NFTFixtures.NFT_ROLLBACK_TEST_VERSION1
import org.vechain.indexer.fixtures.NFTFixtures.NFT_ROLLBACK_TEST_VERSION2
import org.vechain.indexer.fixtures.NFTFixtures.NFT_VIP181
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.ArchiveRepository
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.IdUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.util.*

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {

    @MockK
    lateinit var archiveRepository: ArchiveRepository

    @MockK
    lateinit var nftRepository: NFTRepository

    lateinit var nftService: NFTService

    lateinit var nftEventIndexer: NFTEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftService = NFTService(archiveRepository)
        nftEventIndexer = NFTEventIndexer(nftRepository, nftService, "http://localhost:8669", 0L)
    }

    @Test
    fun `Process block - with NFT transfer events`() {
        val blockNumber = 8L

        every { nftRepository.findAllById(any()) } returns mutableListOf()

        val nftsSlot = slot<List<IndexedNFT>>()
        every { nftRepository.saveAll(capture(nftsSlot)) } returns mutableListOf()

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

        // Verify that archive isn't called
        verify { archiveRepository.saveAll<Archive<*>>(any()) wasNot Called }
    }

    @Test
    fun `Process block - with no NFT transfer events`() {

        nftEventIndexer.processBlock(BLOCK_3_NO_CLAUSES)

        verify { nftRepository wasNot Called }
    }

    // Rollback tests
    @Test
    fun `rollback - successfully restore from archive`() {
        val blockNumber = 16L
        val archiveId = "${NFT_ROLLBACK_TEST_VERSION1.id}-${NFT_ROLLBACK_TEST_VERSION1.version}"

        every { nftRepository.findAllByBlockNumber(blockNumber) } returns mutableListOf(
            NFT_VIP181,
            NFT_ROLLBACK_TEST_VERSION2
        )

        every { archiveRepository.findById(IdUtils.buildHashedId(archiveId)) } returns Optional.of(
            Archive(
                archiveId,
                NFT_ROLLBACK_TEST_VERSION1
            )
        )

        val deleteSlot = slot<List<IndexedNFT>>()
        every { nftRepository.deleteAll(capture(deleteSlot)) } returns Unit

        val nftsSlot = slot<List<IndexedNFT>>()
        every { nftRepository.saveAll(capture(nftsSlot)) } returns listOf()

        nftEventIndexer.rollback(blockNumber)

        val deletedNFTs = deleteSlot.captured
        expectThat(deletedNFTs).hasSize(1)
        compareNFTs(NFT_VIP181, deletedNFTs.first())

        val savedNFTs = nftsSlot.captured
        expectThat(savedNFTs).hasSize(1)
        compareNFTs(NFT_ROLLBACK_TEST_VERSION1, savedNFTs.first())
    }

    @Test
    fun `rollback - no archive record - throws exception`() {
        val blockNumber = 16L
        val archiveId = "${NFT_ROLLBACK_TEST_VERSION1.id}-${NFT_ROLLBACK_TEST_VERSION1.version}"

        every { nftRepository.findAllByBlockNumber(blockNumber) } returns mutableListOf(
            NFT_VIP181,
            NFT_ROLLBACK_TEST_VERSION2
        )

        every { archiveRepository.findById(IdUtils.buildHashedId(archiveId)) } returns Optional.empty()

        expectThrows<Exception> { nftEventIndexer.rollback(blockNumber) }
    }

    private fun compareNFTs(expected: IndexedNFT, actual: IndexedNFT) {
        expect {
            that(actual.id).isEqualTo(expected.id)
            that(actual.tokenId).isEqualTo(expected.tokenId)
            that(actual.contractAddress).isEqualTo(expected.contractAddress)
            that(actual.owner).isEqualTo(expected.owner)
            that(actual.txId).isEqualTo(expected.txId)
            that(actual.blockNumber).isEqualTo(expected.blockNumber)
            that(actual.blockTimestamp).isEqualTo(expected.blockTimestamp)
        }
    }

}
