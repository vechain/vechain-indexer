package org.vechain.indexer.thor.model

import com.fasterxml.jackson.annotation.JsonView

@JsonView(Views.Expanded::class)
data class TxTransfer(val sender: String, val recipient: String, val amount: String)
