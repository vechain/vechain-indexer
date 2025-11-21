package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.annotation.Repeatable
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.thor.Address

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    schema = Schema(type = "string", pattern = Address.REGEX),
    example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
)
@Repeatable
annotation class AddressParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "name") val name: String = "address",
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "A valid address",
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
)
