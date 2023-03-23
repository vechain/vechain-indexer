package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "blocks")
data class Block(
    @Id
    val id: String,
    @Indexed(unique = true)
    val number: Long,
    val size: Long,
    val parentID: String,
    val timestamp: Long,
    val gasLimit: Long,
    val beneficiary: String,
    val gasUsed: Long,
    val totalScore: Long,
    val txsRoot: String,
    val txsFeatures: Int,
    val stateRoot: String,
    val receiptsRoot: String,
    val com: Boolean,
    @Indexed
    val signer: String,
    val isTrunk: Boolean,
    val isFinalized: Boolean,
    val transactions: List<Transaction> = emptyList()
)

