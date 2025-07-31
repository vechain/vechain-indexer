package org.vechain.indexer.fixtures

import org.vechain.indexer.nft.IndexedNft

object NFTFixtures {

    val NFT_VIP181 =
        IndexedNft(
            id = "3eeba42d5c9cce7224250c2e5f153f87e787f134",
            version = 1,
            tokenId = "vip181token",
            contractAddress = "0x8418c039aa38a55b1f1d3742f65521920b65243c",
            owner = "0x0000000000000000000000000000000000000003",
            blockId = "0x000000071fbd72d3ea2ab64456274984fc4533340931399d3844dfee8b3cb3ce",
            blockNumber = 7L,
            blockTimestamp = 1530316880,
            txId = "0x1e52058b941f50120e4d5d463fc3676209d1b879cfe4067f63cc6ddaf3408f00",
        )

    val NFT_ROLLBACK_TEST_VERSION1 =
        IndexedNft(
            id = "64fc91a89710a9365f88261f06b7321f6394fa32",
            version = 1,
            tokenId = "token1",
            contractAddress = "0xab79539086966abd833f43142536bb0e8bca93b1",
            owner = "0x0000000000000000000000000000000000000001",
            blockId = "0x000000061980a0bdfdf2fdddcb91834adc92920ac45c05a4519b8c0bd2d5764a",
            blockNumber = 6L,
            blockTimestamp = 1530316870,
            txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0",
        )

    val NFT_ROLLBACK_TEST_VERSION2 =
        IndexedNft(
            id = "64fc91a89710a9365f88261f06b7321f6394fa32",
            version = 2,
            tokenId = "token1",
            contractAddress = "0xab79539086966abd833f43142536bb0e8bca93b1",
            owner = "0x0000000000000000000000000000000000000002",
            blockId = "0x000000071fbd72d3ea2ab64456274984fc4533340931399d3844dfee8b3cb3ce",
            blockNumber = 7L,
            blockTimestamp = 1530316880,
            txId = "0xfc996d321a96702d0468a60e17b032fe5fa9e3f6edb6a55858eb2e2d5580af8d",
        )
}
