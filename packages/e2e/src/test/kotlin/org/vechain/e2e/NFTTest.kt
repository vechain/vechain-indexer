package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.utils.AddressUtils
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
            .hasSize(102)
            .isA<List<IndexedNFT>>()
            .map { assertValidNft(it) }
    }

    @Test
    fun `get NFTs for address with pagination`() {
        val nfts = VeWorldAPIClient.getNfts(address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa", size = 1)

        expectThat(nfts.data)
            .hasSize(1)
            .isA<List<IndexedNFT>>()
            .map { assertValidNft(it) }
    }

    @Test
    fun `get filtered NFTS`() {
        //Perform regular call to get contract addresses
        val nfts = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE
        )

        expectThat(nfts.data).hasSize(102)

        val firstContractAddress = nfts.data[0].contractAddress
        val nftAmountForFirstContract = nfts.data.count { it.contractAddress == firstContractAddress }

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            contractAddress = firstContractAddress,
            page = 0,
            size = Int.MAX_VALUE
        )

        expectThat(nftsWithQuery.data).hasSize(nftAmountForFirstContract)
    }

    @Test
    fun `get NFT contracts of owner`() {
        val nfts = VeWorldAPIClient.getNftContracts(
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE,
        )

        expectThat(nfts.data)
            .hasSize(2)
            .isA<List<String>>()
            .map { AddressUtils.isValid(it) }
            .all { isTrue() }
    }

    @Test
    fun `get NFT contracts of owner with pagination`() {
        val nfts = VeWorldAPIClient.getNftContracts(
            owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = 1,
        )

        expectThat(nfts.data)
            .hasSize(1)
            .isA<List<String>>()
            .map { AddressUtils.isValid(it) }
            .all { isTrue() }
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