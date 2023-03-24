package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transfer_events")
data class TransferEvent(
    @Id
    val id: String,
    val blockId: String,
    @Indexed
    val blockNumber: Long,
    val txId: String,
    val clauseIndex: Int,
    @Indexed
    val from: String,
    @Indexed
    val to: String,
    val value: String,
    @Indexed
    val tokenAddress: String
)
