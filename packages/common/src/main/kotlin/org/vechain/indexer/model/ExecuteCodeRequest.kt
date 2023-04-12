package org.vechain.indexer.model

data class ExecuteCodeRequest(
    val clauses: List<Clause>,
    val gas: Long = 50000,
    val gasPrice: String = "1000000000000000",
    val caller: String = "0x0000000000000000000000000000000000000000",
    val provedWork: String = "1000",
    val gasPayer: String = "0x0000000000000000000000000000000000000000",
    val expiration: Long = 1000,
    val blockRef: String
)