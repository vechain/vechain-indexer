package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transfer_events")
data class TransferEvent @ConstructorBinding constructor(
    @Id
    val id: String,
    val blockId: String,
    @Indexed(direction = IndexDirection.DESCENDING)
    val blockNumber: Long,
    val txId: String,
    @Indexed
    val from: String,
    @Indexed
    val to: String,
    val value: String,
    @Indexed
    val tokenAddress: String,
    val topics: List<String>,
) {
    val isNFTTransfer: Boolean
        get() = topics.size == 4

    val isFungibleTransfer: Boolean
        get() = topics.size == 3
}