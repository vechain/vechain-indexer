package org.vechain.indexer.thor.model

import com.fasterxml.jackson.annotation.JsonView

@JsonView(Views.Expanded::class)
data class Clause(val to: String?, val value: String, val data: String)
