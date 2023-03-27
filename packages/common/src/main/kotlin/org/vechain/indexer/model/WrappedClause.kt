package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "clauses")
data class WrappedClause(
    @Id
    val id: String? = null,
    val blockId: String? = null,
    @Indexed
    val blockNumber: Long? = null,
    val txId: String? = null,
    val index: Int? = null,
    @Indexed
    val origin: String? = null,
    @Indexed(sparse = true)
    override val to: String? = null,
    override val value: String? = null,
    override val data: String? = null,
    val reverted: Boolean? = null,
    val output: TxOutputs?,
) : IClause {
    constructor(block: Block, tx: ITransaction, clause: Clause, index: Int) : this(
        "${tx.id}-$index", block.id, block.number, tx.id, index, tx.origin,
        clause.to, clause.value, clause.data, tx.reverted, tx.outputs.getOrNull(index)
    )
}