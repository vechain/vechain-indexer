package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transactions")
data class WrappedTransaction(
    @Id
    val id: String? = null,
    val blockId: String? = null,
    @Indexed
    val blockNumber: Long? = null,
    val size: Int? = null,
    val chainTag: Int? = null,
    val blockRef: String? = null,
    val expiration: Long? = null,
    val clauses: List<Clause> = emptyList(),
    val gasPriceCoef: Int? = null,
    val gas: Long? = null,
    val dependsOn: String? = null,
    val nonce: String? = null,
    val gasUsed: Long? = null,
    @Indexed
    val gasPayer: String? = null,
    val paid: String? = null,
    val reward: String? = null,
    val reverted: Boolean? = null,
    @Indexed
    val origin: String? = null,
    @Indexed
    val delegator: String? = null,
    val outputs: List<TxOutputs> = emptyList()
)

data class TxOutputs(
    val contractAddress: String? = null,
    val events: List<TxEvent> = emptyList(),
    val transfers: List<TxTransfer> = emptyList(),
)

data class TxEvent(
    val address: String? = null,
    val topics: List<String> = emptyList(),
    val data: String? = null
)

data class TxTransfer(
    val sender: String? = null,
    val recipient: String? = null,
    val amount: String? = null
)