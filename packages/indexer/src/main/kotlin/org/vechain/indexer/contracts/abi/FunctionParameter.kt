package org.vechain.indexer.contracts.abi

data class FunctionParameter(
  val name: String,
  val type: String,
  val components: List<Any>? = null,
  val internalType: String? = null
)
