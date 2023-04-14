package org.vechain.indexer.fixtures

import org.vechain.indexer.model.Contract

object ContractFixtures {

    /**
     * VIP181 contract with creator = master
     */
    val CONTRACT_WITH_CREATOR_SAME_AS_MASTER = Contract(
        address = "0x1f734d58eb6a349f038c28f112478bf90981c87e",
        blockId = "0x000000067d3b4b3bbefc6efdf463ee8932c52ba6358f675e43ab1e7036678f4e",
        blockNumber = 6L,
        txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0",
        creator = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        master = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        rawData = "rawData",
        isVip180 = false,
        isVip181 = true,
        isErc20 = false,
        isErc721 = true,
        previousMasters = mutableSetOf()
    )

}