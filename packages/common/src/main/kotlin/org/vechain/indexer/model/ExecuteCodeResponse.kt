package org.vechain.indexer.model

data class ExecuteCodeResponse(
    val data: String? = null,
    val events: List<TxEvent>? = null,
    val transfers: List<TxTransfer>? = null,
    val gasUsed: Long? = null,
    val reverted: Boolean? = null,
    val vmError: String? = null
)


