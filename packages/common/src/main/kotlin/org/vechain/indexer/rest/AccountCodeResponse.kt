package org.vechain.indexer.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class AccountCodeResponse @ConstructorBinding constructor(val code: String)
