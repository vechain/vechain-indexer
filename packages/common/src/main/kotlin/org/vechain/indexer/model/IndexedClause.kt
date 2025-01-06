package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonCreator
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction
import org.vechain.indexer.thor.model.TxOutputs

@Document(collection = "clauses")
data class IndexedClause
@ConstructorBinding
@JsonCreator
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val index: Int,
    val origin: String,
    val to: String?,
    val value: String,
    val data: String,
    val reverted: Boolean,
    val output: TxOutputs?,
) : IndexedDocument {
    constructor(
        block: Block,
        tx: Transaction,
        clause: Clause,
        index: Int
    ) : this(
        id = DigestUtils.sha1Hex("${tx.id}-$index"),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
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
