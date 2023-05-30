package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("nfts")
data class NFT @ConstructorBinding constructor(
    /**
     * sha1 hash of `${contractAddress}-${tokenId}`
     */
    @Id
    val id: String,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : IndexedDocument