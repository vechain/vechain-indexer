package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.UUID

@Document(collection = "clauses")
data class WrappedClause (
    @Id
    val id: String,
    val blockId: String,
    val blockNumber: Long,
    val txId: String,
    override val to: String? = null,
    override val value: String,
    override val data: String
): IClause {
    constructor(block: Block, tx: ITransaction, clause: Clause) : this(
        UUID.randomUUID().toString(), block.id, block.number, tx.id,
            clause.to, clause.value, clause.data)
}