package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.thor.Address

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "contractAddress",
    schema = Schema(type = "string", pattern = Address.REGEX),
    description = "The contract address",
    example = "0x0000000000000000000000000000456e65726779",
)
annotation class ContractAddressParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "The contract address",
    @get:AliasFor(annotation = Parameter::class, attribute = "example") val example: String = "",
)
