package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transactions")
data class WrappedTransaction(
    @Id
    override val id: String,
    val blockId: String,
    val blockNumber: Long,
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
    @Indexed
    override val gasPayer: String,
    override val paid: String,
    override val reward: String,
    override val reverted: Boolean,
    @Indexed
    override val origin: String,
    @Indexed
    override val delegator: String?,
    override val outputs: List<TxOutputs>
) : ITransaction {
    constructor(block: Block, tx: ITransaction) : this(
        tx.id, block.id, block.number,
        tx.size, tx.chainTag, tx.blockRef, tx.expiration, tx.clauses,
        tx.gasPriceCoef, tx.gas, tx.dependsOn, tx.nonce, tx.gasUsed, tx.gasPayer,
        tx.paid, tx.reward, tx.reverted, tx.origin, tx.delegator, tx.outputs
    )
}