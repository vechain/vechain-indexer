package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "clauses")
data class WrappedClause(
    @Id
    val id: String,
    val blockId: String,
    val blockNumber: Long,
    val txId: String,
    val index: Int,
    @Indexed
    val origin: String,
    val output: TxOutputs?,
    @Indexed(sparse = true)
    override val to: String? = null,
    override val value: String,
    override val data: String
) : IClause {
    constructor(block: Block, tx: ITransaction, clause: Clause, index: Int) : this(
        "${tx.id}-$index", block.id, block.number, tx.id, index, tx.origin, tx.outputs.getOrNull(index),
        clause.to, clause.value, clause.data
    )
}