package org.vechain.thor.model

data class TxOutputs(
  val contractAddress: String? = null,
  val events: List<TxEvent>,
  val transfers: List<TxTransfer>,
)
