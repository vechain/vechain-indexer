package org.vechain.indexer.thor.model

data class Block(
    val number: Long,
    val id: String,
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
    val signer: String,
    val isTrunk: Boolean,
    val isFinalized: Boolean,
    val transactions: List<Transaction> = emptyList(),
)
