package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class Clause @ConstructorBinding constructor(
    val to: String?,
    val value: String,
    val data: String
)