package org.vechain.indexer.model.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.thor.model.TxEvent
import org.vechain.thor.model.TxTransfer

data class ExecuteCodeResponse
@ConstructorBinding
constructor(
    val data: String,
    val events: List<TxEvent>,
    val transfers: List<TxTransfer>,
    val gasUsed: Long,
    val reverted: Boolean,
    val vmError: String?
)
