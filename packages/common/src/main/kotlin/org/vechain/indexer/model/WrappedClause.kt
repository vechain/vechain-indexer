package org.vechain.indexer.model

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "clauses")
data class WrappedClause @ConstructorBinding constructor(
    @Id
    val id: String,
    val blockId: String,
    @Indexed(direction = IndexDirection.DESCENDING)
    val blockNumber: Long,
    val txId: String,
    val index: Int,
    @Indexed
    val origin: String,
    @Indexed(sparse = true)
    val to: String?,
    val value: String,
    val data: String,
    val reverted: Boolean,
    val output: TxOutputs?,
) {
    constructor(block: Block, tx: Transaction, clause: Clause, index: Int) : this(
        id = DigestUtils.sha1Hex("${tx.id}-$index"),
        blockId = block.id,
        blockNumber = block.blockNumber,
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