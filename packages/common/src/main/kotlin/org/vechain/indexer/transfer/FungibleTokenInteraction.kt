package org.vechain.indexer.transfer

import org.vechain.indexer.IndexedDocument

/** A wallet's first transfer to or from a fungible token contract. */
data class FungibleTokenInteraction(
    val contractAddress: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val walletAddress: String,
) : IndexedDocument
