package org.vechain.indexer.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class ExecuteAccountResponse
@ConstructorBinding
constructor(val balance: String, val energy: String, val hasCode: Boolean = false)
