package org.vechain.indexer.fixtures

import org.vechain.indexer.model.IndexedContract

object ContractFixtures {

    /** VIP181 contract with creator = master */
    val CONTRACT_WITH_CREATOR_SAME_AS_MASTER =
        IndexedContract(
            address = "0xf248673ca9e4b76db70957e463afd521475277cf",
            version = 1,
            blockId = "0x000000067d3b4b3bbefc6efdf463ee8932c52ba6358f675e43ab1e7036678f4e",
            blockNumber = 6L,
            blockTimestamp = 1680177334L,
            txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0",
            creator = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            master = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            rawData = "rawData1",
            isVip180 = false,
            isVip181 = true,
            isErc20 = false,
            isErc721 = true,
            isErc1155 = false,
            isVip210 = false,
            previousMasters = mutableSetOf(),
        )

    val CONTRACT_ROLLBACK_TEST_VERSION1 =
        IndexedContract(
            address = "0xc023b1316daa34b949234a38ce373749e7373d34",
            version = 1,
            blockId = "0x000000061980a0bdfdf2fdddcb91834adc92920ac45c05a4519b8c0bd2d5764a",
            blockNumber = 6L,
            blockTimestamp = 1530316870,
            txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0",
            creator = "0x74bf76d466f999133a398ac033f64f75316941b1",
            master = "0x74bf76d466f999133a398ac033f64f75316941b1",
            rawData = "rawData2",
            isVip180 = false,
            isVip181 = true,
            isErc20 = false,
            isErc721 = true,
            isErc1155 = false,
            isVip210 = false,
            previousMasters = mutableSetOf(),
        )

    val CONTRACT_ROLLBACK_TEST_VERSION2 =
        IndexedContract(
            address = "0xc023b1316daa34b949234a38ce373749e7373d34",
            version = 2,
            blockId = "0x000000071fbd72d3ea2ab64456274984fc4533340931399d3844dfee8b3cb3ce",
            blockNumber = 7L,
            blockTimestamp = 1530316880,
            txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b1",
            creator = "0x74bf76d466f999133a398ac033f64f75316941b1",
            master = "0xb089b15a00528eeb19fca4565df80d9a111bfcf9",
            rawData = "rawData3",
            isVip180 = false,
            isVip181 = true,
            isErc20 = false,
            isErc721 = true,
            isErc1155 = false,
            isVip210 = false,
            previousMasters = mutableSetOf(),
        )
}
