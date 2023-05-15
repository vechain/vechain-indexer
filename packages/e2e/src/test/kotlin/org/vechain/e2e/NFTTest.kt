package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.NFT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
import java.math.BigInteger

class NFTTest {
    @Test
    fun `get NFTs for address`() {
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        expectThat(nfts).hasSize(2)

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
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        expectThat(nfts).hasSize(2)


        //Quick sanity check
        expectThat(nfts[0].contractAddress).isNotEqualTo(nfts[1].contractAddress)

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            nfts[0].contractAddress
        )

        expectThat(nftsWithQuery).hasSize(1)
    }

    fun assertValidNft(nft: NFT) {
        expect {
            that(nft.tokenId).isGreaterThan(BigInteger.valueOf(-1))
            that(nft.contractAddress).isNotEmpty()
            that(nft.blockNumber).isGreaterThan(0)
            that(nft.txId).isNotEmpty()
            that(nft.owner).isNotEmpty()
            that(nft.id).isNotEmpty()
        }
    }
}