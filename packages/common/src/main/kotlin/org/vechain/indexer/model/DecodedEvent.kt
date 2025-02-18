package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Expanded::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DecodedEvent(
    val address: String,
    val topics: List<String>,
    val data: String,
    val name: String? = null,
    val params: Map<String, Any>? = null,
)
