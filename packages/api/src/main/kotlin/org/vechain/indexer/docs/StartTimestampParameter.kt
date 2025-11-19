package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "startTimestamp",
    schema = Schema(type = "integer", format = "int64", minimum = "0"),
    description = "The starting timestamp in seconds since Unix epoch (inclusive)",
    example = "1704067200",
)
annotation class StartTimestampParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = true,
)
