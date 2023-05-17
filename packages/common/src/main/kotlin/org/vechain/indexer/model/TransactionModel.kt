package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.web3j.protocol.core.methods.response.Log

@Document(collection = "transactions")
@CompoundIndex(name = "tx_block_number_idx", def = "{'blockNumber': -1}")
data class Transaction @ConstructorBinding constructor(
    @Id
    val id: String,
    override val blockNumber: Long,
    override val blockId: String,
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
    @Indexed
    val gasPayer: String,
    val paid: String,
    val reward: String,
    val reverted: Boolean,
    @Indexed
    val origin: String,
    val outputs: List<TxOutputs>
) : IndexedDocument {

    constructor(tx: TransactionData) : this(
        id = tx.id,
        blockNumber = tx.blockNumber,
        blockId = tx.blockId,
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

data class TxOutputs(
    val contractAddress: String?,
    val events: List<TxEvent>,
    val transfers: List<TxTransfer>,
)

data class TxEvent(
    val address: String,
    val topics: List<String>,
    val data: String
) {

    /**
     * This can be used for decoding events with Web3J
     */
    fun toLog(): Log {
        val log = Log()
        log.address = address
        log.topics = topics
        log.data = data
        return log
    }
}

data class TxTransfer(
    val sender: String,
    val recipient: String,
    val amount: String
)