package org.vechain.e2e

import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.NFTArchive
import org.web3j.utils.Numeric
import strikt.api.expect
import strikt.assertions.*

class NFTArchivingTest {

    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("TransferEventIndexer")
        VeWorldAPIClient.performIndexerHealthCheck("NFTEventIndexer")
    }

    @Test
    fun `NFTs should be correctly archived when updated`() {
        val nftTransfers = VeWorldAPIClient.getNftTransfers()
        val nfts = VeWorldAPIClient.getNfts()
        val nftArchives = VeWorldAPIClient.getNftArchives()

        // types sanity check
        expect {
            that(nftTransfers).isA<List<IndexedTransferEvent>>()
            that(nfts).isA<List<IndexedNFT>>()
            that(nftArchives).isA<List<NFTArchive>>()
        }

        // verify that the updated NFTs have their corresponding archives
        val nonUpdatedNfts = nfts.filter { it.version == 1 }
        val updatedNfts = nfts.filter { it.version == 2 }
        expect {
            // verify we only have v1 and v2 NFTs
            that(nonUpdatedNfts.size.plus(updatedNfts.size)).isEqualTo(nfts.size)
            // verify we have as much NFT archives as there are NFT updates
            that(updatedNfts.size).isEqualTo(nftArchives.size)
            // verify the archives have the correct version
            that(nftArchives).map { it.data.version }.all { isEqualTo(1) }
            // verify that the updated NFTs IDs match with the archived NFTs IDs
            that(nftArchives.map { it.data.id }).all { isContainedIn(updatedNfts.map { it.id }) }
        }

        // verify that NFTs transfer events correspond to indexed NFTs
        expect {
            that(nftTransfers.size).isEqualTo(nfts.size.plus(nftArchives.size))
            that(nftTransfers.map { buildNftId(it) }).all { isContainedIn(nfts.map { it.id }) }
        }
    }

    @TestOnly
    private fun buildNftId(transferEvent: IndexedTransferEvent): String =
        DigestUtils.sha1Hex(
            "${transferEvent.tokenAddress}-${Numeric.parsePaddedNumberHex(transferEvent.topics[3])}"
        )
}
