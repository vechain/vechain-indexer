package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transfer_events")
data class TransferEvent(
    @Id
    val id: String? = null,
    val blockId: String? = null,
    @Indexed(direction = IndexDirection.DESCENDING)
    val blockNumber: Long? = null,
    val txId: String? = null,
    @Indexed
    val from: String? = null,
    @Indexed
    val to: String? = null,
    val value: String? = null,
    @Indexed
    val tokenAddress: String? = null,
    val topics: List<String> = emptyList(),
) {
    val isNFTTransfer: Boolean
        get() = topics.size == 4

    val isFungibleTransfer: Boolean
        get() = topics.size == 3
}