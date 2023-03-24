package org.vechain.indexer.model

interface ITransaction {
    val id: String
    val size: Int
    val chainTag: Int
    val blockRef: String
    val expiration: Long
    val clauses: List<Clause>
    val gasPriceCoef: Int
    val gas: Long
    val dependsOn: String?
    val nonce: String
    val gasUsed: Long
    val gasPayer: String
    val paid: String
    val reward: String
    val reverted: Boolean
    val origin: String
    val delegator: String?
    val outputs: List<TxOutputs>
}