package org.vechain.indexer.transfer

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.utils.IdUtils.generateId

/**
 * Used to track fungible token contracts that a wallet has interacted with. If a user has sent or
 * received a token from a fungible token contract, an entry will be created here.
 */
@Document(collection = "fungible_token_interactions")
data class FungibleTokenInteraction
@ConstructorBinding
constructor(
    @Id val id: String,
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
