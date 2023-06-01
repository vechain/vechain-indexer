package org.vechain.thor.model

data class TxTransfer(
    val sender: String,
    val recipient: String,
    val amount: String
)