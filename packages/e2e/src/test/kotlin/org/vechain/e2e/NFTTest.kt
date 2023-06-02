package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedNFT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class NFTTest {
    @Test
    fun `get NFTs for address`() {
        val nfts = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE,
        )

        expectThat(nfts.data)
            .isNotNull()
            .hasSize(102)

        nfts.data!!.forEach { nft ->
            assertValidNft(nft)
        }
    }

    @Test
    fun `get NFTs for address with pagination`() {
        val nfts = VeWorldAPIClient.getNfts(address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa", size = 1)

        expectThat(nfts.data)
            .isNotNull()
            .hasSize(1)

        nfts.data!!.forEach { nft ->
            assertValidNft(nft)
        }

    }

    @Test
    fun `get filtered NFTS`() {
        //Perform regular call to get contract addresses
        val nfts = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE
        )

        expect {
            that(nfts.data)
                .isNotNull()
                .hasSize(102)
            that(nfts.data!!.distinctBy { it.contractAddress }.size).isEqualTo(2)
        }

        val firstContractAddress = nfts.data!![0].contractAddress
        val nftAmountForFirstContract = nfts.data!!.count { it.contractAddress == firstContractAddress }

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            contractAddress = firstContractAddress,
            page = 0,
            size = Int.MAX_VALUE
        )

        expectThat(nftsWithQuery.data)
            .isNotNull()
            .hasSize(nftAmountForFirstContract)
    }

    fun assertValidNft(nft: IndexedNFT) {
        expect {
            that(nft.tokenId).isNotEmpty()
            that(nft.contractAddress).isNotEmpty()
            that(nft.blockNumber).isGreaterThan(0)
            that(nft.txId).isNotEmpty()
            that(nft.owner).isNotEmpty()
            that(nft.id).isNotEmpty()
        }
    }
}