package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.thor.Address

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "wallet",
    schema = Schema(type = "string", pattern = Address.REGEX),
    description = "Wallet address.",
)
annotation class WalletParameter(
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
