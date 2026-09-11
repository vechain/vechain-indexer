package org.vechain.indexer.nft

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM
import strikt.api.expect
import strikt.assertions.isEqualTo

internal class NftServiceTest {
    private val repository = mockk<NftWriteRepository>(relaxed = true)
    private val nftService = NftService(repository)

    @Test
    fun `save hands the states to the writer and skips an empty list`() {
        val nfts = nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER)

        nftService.save(nfts)
        nftService.save(emptyList())

        verify(exactly = 1) { repository.save(nfts) }
    }

    @Test
    fun `processBlock projects valid NFT transfers`() {
        val result = nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER)

        expect {
            that(result.size).isEqualTo(2)
            that(result[0].owner).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].contractAddress).isEqualTo("0x14091cc9ae249f26eaf41a5a21207931162a2826")
            that(result[0].tokenId).isEqualTo("1")
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6484")
            that(result[0].blockNumber).isEqualTo(21245998L)
            that(result[0].blockTimestamp).isEqualTo(1742989050L)

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
    fun `processBlock handles an empty list`() {
        expect { that(nftService.processBlock(emptyList()).size).isEqualTo(0) }
    }

    @Test
    fun `processBlock throws when the 'to' parameter is missing`() {
        assertThrows<NullPointerException> {
            nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM)
        }
    }

    @Test
    fun `processBlock throws when the 'tokenId' parameter is missing`() {
        assertThrows<IllegalArgumentException> {
            nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM)
        }
    }

    @Test
    fun `a token transferred in two blocks yields a state per block, oldest first`() {
        val result = nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE)

        expect {
            that(result.size).isEqualTo(2)
            that(result.map { it.blockNumber }).isEqualTo(listOf(11245998L, 21246000L))
            that(result.last().owner).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
        }
    }

    @Test
    fun `block order does not depend on the list order`() {
        val result = nftService.processBlock(INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE.reversed())

        expect {
            that(result.map { it.blockNumber }).isEqualTo(listOf(11245998L, 21246000L))
            that(result.last().owner).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
        }
    }
}
