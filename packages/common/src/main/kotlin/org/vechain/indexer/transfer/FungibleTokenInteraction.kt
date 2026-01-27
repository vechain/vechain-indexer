package org.vechain.indexer.transfer

import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.utils.IdUtils.generateId

/**
 * Used to track fungible token contracts that a wallet has interacted with. If a user has sent or
 * received a token from a fungible token contract, an entry will be created here.
 */
data class FungibleTokenInteraction(
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val contractAddress: String,
    val walletAddress: String,
) : IndexedDocument {
    constructor(
        contractAddress: String,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        walletAddress: String,
    ) : this(
        id = generateId(contractAddress, walletAddress),
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        contractAddress = contractAddress,
        walletAddress = walletAddress,
    )
}
