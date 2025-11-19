package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "endTimestamp",
    schema = Schema(type = "integer", format = "int64", minimum = "0"),
    description =
        "The ending timestamp in seconds since Unix epoch (inclusive). Must be greater than or equal to startTimestamp.",
    example = "1704153600",
)
annotation class EndTimestampParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = true,
)
