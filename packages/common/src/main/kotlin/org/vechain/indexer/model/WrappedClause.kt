package org.vechain.indexer.model

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "clauses")
@CompoundIndex(name = "clause_block_number_idx", def = "{'blockNumber': -1}")
data class WrappedClause @ConstructorBinding constructor(
    @Id
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    @Indexed
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
) : IndexedDocument {
    constructor(block: Block, tx: Transaction, clause: Clause, index: Int) : this(
        id = DigestUtils.sha1Hex("${tx.id}-$index"),
        blockId = block.blockId,
        blockNumber = block.blockNumber,
        blockTimestamp = block.blockTimestamp,
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