package org.vechain.indexer.model

import org.apache.commons.codec.digest.DigestUtils
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
    val to: String? = null,
    val value: String? = null,
    val data: String? = null,
    val reverted: Boolean? = null,
    val output: TxOutputs? = null,
) {
    constructor(block: Block, tx: WrappedTransaction, clause: Clause, index: Int) : this(
        id = DigestUtils.sha1Hex("${tx.id}-$index"),
        blockId = block.id,
        blockNumber = block.number,
        txId = tx.id,
        index = index,
        origin = tx.origin,
        to = clause.to,
        value = clause.value,
        data = clause.data,
        reverted = tx.reverted,
        output = tx.outputs.getOrNull(index)
    )
}