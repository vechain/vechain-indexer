package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "blocks")
data class Block(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val number: Long? = null,
    val size: Long? = null,
    val parentID: String? = null,
    val timestamp: Long? = null,
    val gasLimit: Long? = null,
    val beneficiary: String? = null,
    val gasUsed: Long? = null,
    val totalScore: Long? = null,
    val txsRoot: String? = null,
    val txsFeatures: Int? = null,
    val stateRoot: String? = null,
    val receiptsRoot: String? = null,
    val com: Boolean? = null,
    @Indexed
    val signer: String? = null,
    val isTrunk: Boolean? = null,
    val isFinalized: Boolean? = null,
    val transactions: List<WrappedTransaction> = emptyList()
)