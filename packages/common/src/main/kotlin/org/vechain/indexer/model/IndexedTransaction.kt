package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.*

@Document(collection = "transactions")
@JsonView(Views.Public::class)
data class IndexedTransaction
@ConstructorBinding
@JsonCreator
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val type: Long?,
    val size: Long,
    val chainTag: Long,
    val blockRef: String,
    val expiration: Long,
    @JsonView(Views.Expanded::class) val clauses: List<Clause>,
    val gasPriceCoef: Long?,
    val gas: Long,
    val maxFeePerGas: String?,
    val maxPriorityFeePerGas: String?,
    val dependsOn: String?,
    val nonce: String,
    val gasUsed: Long,
    val gasPayer: String,
    val paid: String,
    val reward: String,
    val reverted: Boolean,
    val origin: String,
    @JsonView(Views.Expanded::class) val outputs: List<DecodedOutputs>,
) : IndexedDocument {
    constructor(
        block: Block,
        tx: Transaction,
        decodedOutputs: List<DecodedOutputs>,
    ) : this(
        id = tx.id,
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        type = tx.type,
        size = tx.size,
        chainTag = tx.chainTag,
        blockRef = tx.blockRef,
        expiration = tx.expiration,
        clauses = tx.clauses,
        gasPriceCoef = tx.gasPriceCoef,
        gas = tx.gas,
        maxFeePerGas = tx.maxFeePerGas,
        maxPriorityFeePerGas = tx.maxPriorityFeePerGas,
        dependsOn = tx.dependsOn,
        nonce = tx.nonce,
        gasUsed = tx.gasUsed,
        gasPayer = tx.gasPayer,
        paid = tx.paid,
        reward = tx.reward,
        reverted = tx.reverted,
        origin = tx.origin,
        outputs = decodedOutputs,
    )
}
