package org.vechain.indexer.thor.model

import com.fasterxml.jackson.annotation.JsonView

@JsonView(Views.Expanded::class)
data class TxOutputs(
    val contractAddress: String? = null,
    val events: List<TxEvent>,
    val transfers: List<TxTransfer>,
)
