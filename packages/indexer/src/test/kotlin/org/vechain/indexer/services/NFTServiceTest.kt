package org.vechain.indexer.services

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.IndexedNFTEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER
import org.vechain.indexer.fixtures.IndexedNFTEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE
import org.vechain.indexer.fixtures.IndexedNFTEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM
import org.vechain.indexer.fixtures.IndexedNFTEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@ExtendWith(MockKExtension::class)
internal class NFTServiceTest {
    @MockK lateinit var repository: NFTRepository
    @MockK lateinit var nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>

    private lateinit var nftService: NFTService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftService = NFTService(repository, nftArchiveService)
    }

    // Update tests

    @Test
    fun `update - should save updated and existing records`() {
        val updated =
            listOf(
                IndexedNFT(
                    id = "nft1",
                    version = 1,
                    owner = "owner1",
                    contractAddress = "contract1",
                    tokenId = "1",
                    txId = "tx1",
                    blockId = "block1",
                    blockNumber = 1L,
                    blockTimestamp = 1L
                )
            )
        val existing =
            listOf(
                IndexedNFT(
                    id = "nft2",
                    version = 1,
                    owner = "owner2",
                    contractAddress = "contract2",
                    tokenId = "2",
                    txId = "tx2",
                    blockId = "block2",
                    blockNumber = 2L,
                    blockTimestamp = 2L
                )
            )

        every { repository.saveAll(updated) } returns updated
        every { nftArchiveService.saveAll(existing) } just Runs

        nftService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { nftArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if updated is empty`() {
        val updated = emptyList<IndexedNFT>()
        val existing =
            listOf(
                IndexedNFT(
                    id = "nft2",
                    version = 1,
                    owner = "owner2",
                    contractAddress = "contract2",
                    tokenId = "2",
                    txId = "tx2",
                    blockId = "block2",
                    blockNumber = 2L,
                    blockTimestamp = 2L
                )
            )

        every { repository.saveAll(updated) } returns updated
        every { nftArchiveService.saveAll(existing) } just Runs

        nftService.update(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 1) { nftArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if existing is empty`() {
        val updated =
            listOf(
                IndexedNFT(
                    id = "nft1",
                    version = 1,
                    owner = "owner1",
                    contractAddress = "contract1",
                    tokenId = "1",
                    txId = "tx1",
                    blockId = "block1",
                    blockNumber = 1L,
                    blockTimestamp = 1L
                )
            )
        val existing = emptyList<IndexedNFT>()

        every { repository.saveAll(updated) } returns updated
        every { nftArchiveService.saveAll(existing) } just Runs

        nftService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 0) { nftArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll with empty lists`() {
        val updated = emptyList<IndexedNFT>()
        val existing = emptyList<IndexedNFT>()

        every { repository.saveAll(updated) } returns updated
        every { nftArchiveService.saveAll(existing) } just Runs

        nftService.update(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 0) { nftArchiveService.saveAll(existing) }
    }

    // parseRecords

    @Test
    fun `parseRecords - should parse valid NFT transfer records`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER

        val result = nftService.parseRecords(events, emptyList())

        expect {
            that(result.size).isEqualTo(2)
            that(result[0].version).isEqualTo(1)
            that(result[0].owner).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].contractAddress).isEqualTo("0x14091cc9ae249f26eaf41a5a21207931162a2826")
            that(result[0].tokenId).isEqualTo("1")
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6484")
            that(result[0].blockNumber).isEqualTo(21245998L)
            that(result[0].blockTimestamp).isEqualTo(1742989050L)

            that(result[1].version).isEqualTo(1)
            that(result[1].owner).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
            that(result[1].contractAddress).isEqualTo("0x14091cc9ae249f26eaf41a5a21207931162a2826")
            that(result[1].tokenId).isEqualTo("2")
            that(result[1].blockId)
                .isEqualTo("0x0144302f01d216b50110ad8d9ff03d37f3559a03c559a9cc872f6ba8b9594f56")
            that(result[1].blockNumber).isEqualTo(21245999L)
            that(result[1].blockTimestamp).isEqualTo(1742989060L)
        }
    }

    @Test
    fun `parseRecords - should handle empty list`() {
        val result = nftService.parseRecords(emptyList(), emptyList())

        expect { that(result.size).isEqualTo(0) }
    }

    @Test
    fun `parseRecords - should update an existing record`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER

        val existing =
            listOf(
                IndexedNFT(
                    id = "f1022dd108d235addf1cab2135ca5430a4805c5c",
                    version = 1,
                    owner = "old_owner",
                    contractAddress = "0x14091cc9ae249f26eaf41a5a21207931162a2826",
                    tokenId = "1",
                    txId = "old_tx",
                    blockId = "old_block",
                    blockNumber = 1L,
                    blockTimestamp = 1L
                )
            )

        val result = nftService.parseRecords(events, existing)

        expectThat(result.size).isEqualTo(2)

        // Check if the updated records is updated correctly
        // Find updated record in result
        val up = result.find { it.id == existing[0].id }
        expect {
            that(up).isNotNull()
            that(up?.version).isEqualTo(2)
            that(up?.owner).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(up?.contractAddress).isEqualTo("0x14091cc9ae249f26eaf41a5a21207931162a2826")
            that(up?.tokenId).isEqualTo("1")
            that(up?.blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6484")
            that(up?.blockNumber).isEqualTo(21245998L)
            that(up?.blockTimestamp).isEqualTo(1742989050L)
        }

        // Check if the other records is not touched
        // find the other record
        val other = result.find { it.id != existing[0].id }
        expect {
            that(other).isNotNull()
            that(other?.version).isEqualTo(1)
        }
    }

    @Test
    fun `parseRecords - should throw exception when 'to' parameter is missing`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM

        assertThrows<NullPointerException> { nftService.parseRecords(events, emptyList()) }
    }

    @Test
    fun `parseRecords - should throw exception when 'tokenId' parameter is missing`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM

        assertThrows<NullPointerException> { nftService.parseRecords(events, emptyList()) }
    }

    @Test
    fun `parseRecords - should handle duplicate records`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE

        val result = nftService.parseRecords(events, emptyList())

        expect {
            that(result.size).isEqualTo(1)
            that(result[0].owner).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
            that(result[0].blockNumber).isEqualTo(21246000L)
        }
    }

    @Test
    fun `parseRecords - should handle duplicate records - incorrect list order`() {
        val events = INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE.reversed()

        val result = nftService.parseRecords(events, emptyList())

        expect {
            that(result.size).isEqualTo(1)
            that(result[0].owner).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
            that(result[0].blockNumber).isEqualTo(21246000L)
        }
    }
}
