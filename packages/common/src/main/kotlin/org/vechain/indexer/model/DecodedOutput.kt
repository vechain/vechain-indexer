package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.thor.model.TxTransfer
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Expanded::class)
data class DecodedOutputs(
    val contractAddress: String?,
    val events: List<DecodedEvent>,
    val transfers: List<TxTransfer>,
)
