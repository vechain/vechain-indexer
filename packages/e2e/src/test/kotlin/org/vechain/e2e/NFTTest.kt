package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.NFT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty

class NFTTest {
    @Test
    fun `get NFTs for address`() {
        val nfts = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE,
        )

        expectThat(nfts).hasSize(102)

        nfts.forEach { nft ->
            assertValidNft(nft)
        }
    }

    @Test
    fun `get NFTs for address with pagination`() {
        val nfts = VeWorldAPIClient.getNfts(address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa", size = 1)

        expectThat(nfts).hasSize(1)

        nfts.forEach { nft ->
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
            that(nfts).hasSize(102)
            that(nfts.distinctBy { it.contractAddress }.size).isEqualTo(2)
        }

        val firstContractAddress = nfts[0].contractAddress
        val nftAmountForFirstContract = nfts.count { it.contractAddress == firstContractAddress }

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            contractAddress = firstContractAddress,
            page = 0,
            size = Int.MAX_VALUE
        )

        expectThat(nftsWithQuery).hasSize(nftAmountForFirstContract)
    }

    fun assertValidNft(nft: NFT) {
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