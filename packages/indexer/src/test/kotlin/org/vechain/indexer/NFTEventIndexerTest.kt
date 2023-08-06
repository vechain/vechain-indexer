package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.util.*
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.exception.ArchiveException
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_3_NO_CLAUSES
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_8_MULTIPLE_CLAUSES
import org.vechain.indexer.fixtures.NFTFixtures.NFT_ROLLBACK_TEST_VERSION1
import org.vechain.indexer.fixtures.NFTFixtures.NFT_ROLLBACK_TEST_VERSION2
import org.vechain.indexer.fixtures.NFTFixtures.NFT_VIP181
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.ArchiveRepository
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.IdUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {

    @MockK lateinit var archiveRepository: ArchiveRepository

    @MockK lateinit var nftRepository: NFTRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var archiveService: ArchiveService
    private lateinit var nftService: NFTService

    private lateinit var nftEventIndexer: NFTEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        archiveService = ArchiveService(archiveRepository, mongoTemplate)
        nftService = NFTService(nftRepository, archiveService)
        nftEventIndexer =
            NFTEventIndexer(
                nftService,
                archiveService,
                DefaultThorClient("http://localhost:8669"),
                nftRepository,
                0L,
                1000L
            )
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
            that(nftEvent.id)
                .isEqualTo(DigestUtils.sha1Hex("0x1f734d58eb6a349f038c28f112478bf90981c87e-1"))
            that(nftEvent.tokenId).isEqualTo("1")
            that(nftEvent.contractAddress).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(nftEvent.owner).isEqualTo("0x361277d1b27504f36a3b33d3a52d1f8270331b8c")
            that(nftEvent.txId)
                .isEqualTo("0x039d5f9bd7ffadd11a4f5888e88f459b7ff349c8c06e820f3102fa0779867ac6")
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
        val archiveId = IdUtils.buildArchiveId(NFT_ROLLBACK_TEST_VERSION1)

        val deleteSlot = mutableListOf<Query>()
        val nftsSlot = mutableListOf<IndexedNFT>()

        val bulkOps: BulkOperations = mockk(relaxed = true)

        // Mock the entries in the NFT collection for rollback number
        every { mongoTemplate.find<IndexedNFT>(any(), any()) } returns
            mutableListOf(NFT_VIP181, NFT_ROLLBACK_TEST_VERSION2)
        // Mocking bulk ops allows us to capture the NFTs that get rolled back
        every { mongoTemplate.bulkOps(any(), IndexedNFT::class.java) } returns bulkOps
        every { bulkOps.replaceOne(any(), capture(nftsSlot)) } returns bulkOps
        every { bulkOps.remove(capture(deleteSlot)) } returns bulkOps
        every { archiveRepository.deleteAllById(any()) } returns Unit
        every { archiveRepository.findAllById(arrayListOf(archiveId)) } returns
            listOf(Archive(archiveId, NFT_ROLLBACK_TEST_VERSION1))

        nftEventIndexer.rollback(blockNumber)

        expect {
            that(deleteSlot.size).isEqualTo(1)
            that(deleteSlot[0].queryObject["_id"]).isEqualTo(NFT_VIP181.id)
        }

        expectThat(nftsSlot).hasSize(1)
        compareNFTs(NFT_ROLLBACK_TEST_VERSION1, nftsSlot.first())
    }

    @Test
    fun `rollback - no archive record - throws exception`() {
        val blockNumber = 16L

        val deletedArchives = slot<List<String>>()

        every { mongoTemplate.find<IndexedNFT>(any(), any()) } returns
            mutableListOf(NFT_VIP181, NFT_ROLLBACK_TEST_VERSION2)
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns mockk(relaxed = true)
        every { archiveRepository.findAllById(any()) } returns emptyList()
        every { archiveRepository.deleteAllById(capture(deletedArchives)) } returns Unit

        expectThrows<ArchiveException> { nftEventIndexer.rollback(blockNumber) }
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
