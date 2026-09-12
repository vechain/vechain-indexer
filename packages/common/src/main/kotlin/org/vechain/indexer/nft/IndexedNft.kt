package org.vechain.indexer.nft

import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
data class IndexedNft
@ConstructorBinding
constructor(
    /** sha1 hash of `${contractAddress}-${tokenId}` */
    val id: String,
    val tokenId: String,
    val contractAddress: String,
    val owner: String,
    val txId: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : IndexedDocument
