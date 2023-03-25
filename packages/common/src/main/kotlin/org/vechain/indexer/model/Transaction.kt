package org.vechain.indexer.model


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