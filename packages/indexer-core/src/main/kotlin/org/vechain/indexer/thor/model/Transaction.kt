package org.vechain.indexer.thor.model

data class Transaction(
    val id: String,
    val chainTag: Long,
    val blockRef: String,
    val expiration: Long,
    val clauses: List<Clause>,
    val gasPriceCoef: Long,
    val gas: Long,
    val origin: String,
    val delegator: String? = null,
    val nonce: String,
    val dependsOn: String? = null,
    val size: Long,
    val gasUsed: Long,
    val gasPayer: String,
    val paid: String,
    val reward: String,
    val reverted: Boolean,
    val outputs: List<TxOutputs>
)
