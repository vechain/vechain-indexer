package org.vechain.indexer.nft

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins the encodings: bare sha1 id, `0x` hex elsewhere, base-10 token ids up to uint256. */
class NftRowMappingTest {

    private val nft =
        IndexedNft(
            id = "3eeba42d5c9cce7224250c2e5f153f87e787f134",
            tokenId = (BigInteger.TWO.pow(256) - BigInteger.ONE).toString(),
            contractAddress = "0x8418c039aa38a55b1f1d3742f65521920b65243c",
            owner = "0x0000000000000000000000000000000000000003",
            txId = "0x1e52058b941f50120e4d5d463fc3676209d1b879cfe4067f63cc6ddaf3408f00",
            blockNumber = 7L,
            blockId = "0x000000071fbd72d3ea2ab64456274984fc4533340931399d3844dfee8b3cb3ce",
            blockTimestamp = 1530316880,
        )

    @Test
    fun `an nft survives flatten and assemble`() {
        assertEquals(nft, NftRowMapping.assemble(NftRowMapping.flatten(nft)))
        assertEquals(
            nft.copy(tokenId = "0"),
            NftRowMapping.assemble(NftRowMapping.flatten(nft.copy(tokenId = "0"))),
        )
    }

    @Test
    fun `the id is twenty bytes and the token id keeps every digit`() {
        val row = NftRowMapping.flatten(nft)
        assertEquals(20, row.id.size)
        assertEquals(78, row.tokenId.precision())
    }
}
