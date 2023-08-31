package org.vechain.indexer.thor.model

import com.fasterxml.jackson.annotation.JsonView

@JsonView(Views.Expanded::class)
data class TxEvent(val address: String, val topics: List<String>, val data: String)
