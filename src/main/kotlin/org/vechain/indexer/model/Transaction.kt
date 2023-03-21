package org.vechain.indexer.model

data class Transaction(
        override val id: String,
        override val size: Int,
        override val chainTag: Int,
        override val blockRef: String,
        override val expiration: Long,
        override val clauses: List<Clause>,
        override val gasPriceCoef: Int,
        override val gas: Long,
        override val dependsOn: String?,
        override val nonce: String,
        override val gasUsed: Long,
        override val gasPayer: String,
        override val paid: String,
        override val reward: String,
        override val reverted: Boolean,
        override val origin: String,
        override val delegator: String?,
        override val outputs: List<Any>): ITransaction