package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transactions")
data class WrappedTransaction(
    @Id
    override val id: String? = null,
    val blockId: String? = null,
    @Indexed
    val blockNumber: Long? = null,
    override val size: Int? = null,
    override val chainTag: Int? = null,
    override val blockRef: String? = null,
    override val expiration: Long? = null,
    override val clauses: List<Clause> = emptyList(),
    override val gasPriceCoef: Int? = null,
    override val gas: Long? = null,
    override val dependsOn: String? = null,
    override val nonce: String? = null,
    override val gasUsed: Long? = null,
    @Indexed
    override val gasPayer: String? = null,
    override val paid: String? = null,
    override val reward: String? = null,
    override val reverted: Boolean? = null,
    @Indexed
    override val origin: String? = null,
    @Indexed
    override val delegator: String? = null,
    override val outputs: List<TxOutputs> = emptyList()
) : ITransaction {
    constructor(block: Block, tx: ITransaction) : this(
        tx.id, block.id, block.number,
        tx.size, tx.chainTag, tx.blockRef, tx.expiration, tx.clauses,
        tx.gasPriceCoef, tx.gas, tx.dependsOn, tx.nonce, tx.gasUsed, tx.gasPayer,
        tx.paid, tx.reward, tx.reverted, tx.origin, tx.delegator, tx.outputs
    )
}