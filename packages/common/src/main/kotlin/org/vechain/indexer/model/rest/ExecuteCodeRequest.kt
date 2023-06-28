package org.vechain.indexer.model.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.thor.model.Clause

data class ExecuteCodeRequest
@ConstructorBinding
constructor(
    val clauses: List<Clause>,
    val gas: Long = 10000000,
    val gasPrice: String = "1000000000000000",
    val caller: String = "0x0000000000000000000000000000000000000000",
    val provedWork: String = "1000",
    val gasPayer: String = "0x0000000000000000000000000000000000000000",
    val expiration: Long = 1000,
    val blockRef: String
)
