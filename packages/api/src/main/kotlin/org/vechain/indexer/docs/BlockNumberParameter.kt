package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS,
)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    schema =
        Schema(type = "integer", format = "int64", minimum = "0", maximum = "9223372036854775807")
)
annotation class BlockNumberParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "name")
    val name: String = "blockNumber",
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Optional block number filter.",
    @get:AliasFor(annotation = Parameter::class, attribute = "example")
    val example: String = "12345678",
)
