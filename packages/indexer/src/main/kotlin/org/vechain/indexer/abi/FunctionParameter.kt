package org.vechain.indexer.abi

data class FunctionParameter(
    val name: String,
    val type: String,
    val components: List<Any>? = null,
    val internalType: String? = null
)
