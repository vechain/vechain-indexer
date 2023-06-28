package org.vechain.indexer.model.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class AccountCodeResponse @ConstructorBinding constructor(val code: String)
