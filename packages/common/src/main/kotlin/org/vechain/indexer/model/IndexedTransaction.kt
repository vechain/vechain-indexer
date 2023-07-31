package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.thor.model.*

@Document(collection = "transactions")
@JsonView(Views.Public::class)
data class IndexedTransaction
@ConstructorBinding
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val size: Long,
    val chainTag: Long,
    val blockRef: String,
    val expiration: Long,
    @JsonView(Views.Expanded::class) val clauses: List<Clause>,
    val gasPriceCoef: Long,
    val gas: Long,
    val dependsOn: String?,
    val nonce: String,
    val gasUsed: Long,
    val gasPayer: String,
    val paid: String,
    val reward: String,
    val reverted: Boolean,
    val origin: String,
    @JsonView(Views.Expanded::class) val outputs: List<TxOutputs>
) : IndexedDocument {

    constructor(
        block: Block,
        tx: Transaction
    ) : this(
        id = tx.id,
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        size = tx.size,
        chainTag = tx.chainTag,
        blockRef = tx.blockRef,
        expiration = tx.expiration,
        clauses = tx.clauses,
        gasPriceCoef = tx.gasPriceCoef,
        gas = tx.gas,
        dependsOn = tx.dependsOn,
        nonce = tx.nonce,
        gasUsed = tx.gasUsed,
        gasPayer = tx.gasPayer,
        paid = tx.paid,
        reward = tx.reward,
        reverted = tx.reverted,
        origin = tx.origin,
        outputs = tx.outputs
    )
}
