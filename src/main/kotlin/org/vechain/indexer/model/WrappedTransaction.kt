package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transactions")
data class WrappedTransaction(
        val blockId: String,
        val blockNumber: Long,
        @Id
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
        override val outputs: List<Any>): ITransaction {
        constructor(block: Block, tx: ITransaction) : this( block.id, block.number,
                tx.id, tx.size, tx.chainTag, tx.blockRef, tx.expiration, tx.clauses,
                tx.gasPriceCoef, tx.gas, tx.dependsOn, tx.nonce, tx.gasUsed, tx.gasPayer,
                tx.paid, tx.reward, tx.reverted, tx.origin, tx.delegator, tx.outputs)
}