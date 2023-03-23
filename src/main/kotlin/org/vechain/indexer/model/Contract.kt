package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "contracts")
data class Contract(
    @Id
    val address: String,
    val blockId: String,
    val blockNumber: Long,
    val txId: String,
    val clauseIndex: Int,
    @Indexed
    val creator: String,
    val rawData: String
)
