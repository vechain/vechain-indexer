package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.model.rest.BlockTransaction

data class TransactionData @ConstructorBinding constructor(
    val id: String,
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val size: Long,
    val chainTag: Long,
    val blockRef: String,
    val expiration: Long,
    val clauses: List<Clause>,
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
    val outputs: List<TxOutputs>
) {
    constructor(blockId: String, blockNumber: Long, blockTimestamp: Long, tx: BlockTransaction) : this(
        id = tx.id,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
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