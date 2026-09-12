package org.vechain.indexer.nft

import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.HexUtils

/** One NFT collection's blacklist flag as of a block; the newest row per collection is current. */
data class NftBlacklistState(
    val contractAddress: String,
    val isBlacklisted: Boolean,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument {
    init {
        require(contractAddress == HexUtils.normalise(contractAddress)) {
            "contractAddress must be normalised: $contractAddress"
        }
    }
}
