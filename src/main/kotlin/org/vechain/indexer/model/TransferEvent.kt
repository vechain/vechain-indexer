package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transfer_events")
data class TransferEvent(
        @Id
        val id: String,
        val blockId: String,
        val blockNumber: Long,
        val txId: String,
        val clauseIndex: Int,
        val from: String,
        val to: String,
        val value: String,
        val tokenAddress: String)
