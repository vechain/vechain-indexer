package org.vechain.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedNFT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class NFTTest {

    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("NFTEventIndexer")
    }

    @Test
    fun `get NFTs for address`() {
        val nfts =
            VeWorldAPIClient.getNfts(
                address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                page = 0,
            )

        expectThat(nfts.data).hasSize(102).isA<List<IndexedNFT>>()
        expectThat(nfts.pagination.hasNext).isFalse()

        nfts.data.forEach { nft: IndexedNFT -> assertValidNft(nft) }
    }

    @Test
    fun `get NFTs for address with pagination`() {
        val nfts =
            VeWorldAPIClient.getNfts(
                address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                size = 1
            )

        expectThat(nfts.data).hasSize(1).isA<List<IndexedNFT>>()
        expectThat(nfts.pagination.hasNext).isTrue()

        nfts.data.forEach { nft: IndexedNFT -> assertValidNft(nft) }
    }

    @Test
    fun `get filtered NFTS`() {
        // Perform regular call to get contract addresses
        val nfts =
            VeWorldAPIClient.getNfts(
                address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                page = 0,
            )

        expectThat(nfts.data).hasSize(102)
        expectThat(nfts.pagination.hasNext).isFalse()

        val firstNft: IndexedNFT = nfts.data[0]
        val firstContractAddress = firstNft.contractAddress
        val nftAmountForFirstContract =
            nfts.data.count { nft: IndexedNFT -> nft.contractAddress == firstContractAddress }

        // Get filtered NFTs
        val nftsWithQuery =
            VeWorldAPIClient.getNfts(
                address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                contractAddress = firstContractAddress,
                page = 0,
            )

        expectThat(nftsWithQuery.data).hasSize(nftAmountForFirstContract)
    }

    @Test
    fun `get NFT contracts of owner`() {
        val nfts =
            VeWorldAPIClient.getNftContracts(
                owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                page = 0,
            )

        expectThat(nfts.data).hasSize(2)
        expectThat(nfts.pagination.hasNext).isFalse()
        nfts.data.forEach { contract: String -> assertValidContract(contract) }
    }

    @Test
    fun `get NFT contracts of owner with pagination`() {
        val nfts =
            VeWorldAPIClient.getNftContracts(
                owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                page = 0,
                size = 1,
            )

        expectThat(nfts.data).hasSize(1)
        expectThat(nfts.pagination.hasNext).isTrue()
        nfts.data.forEach { contract: String -> assertValidContract(contract) }
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

    fun assertValidContract(contract: String) {
        expectThat(Address(contract).isValid()).isTrue()
    }
}
