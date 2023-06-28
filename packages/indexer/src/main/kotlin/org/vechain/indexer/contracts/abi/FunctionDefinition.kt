package org.vechain.indexer.contracts.abi

data class FunctionDefinition(
  val type: String = "function",
  val name: String,
  val inputs: List<FunctionParameter> = emptyList(),
  val outputs: List<FunctionParameter> = emptyList(),
  val constant: Boolean? = null,
  val payable: Boolean? = null,
  val stateMutability: String
)
