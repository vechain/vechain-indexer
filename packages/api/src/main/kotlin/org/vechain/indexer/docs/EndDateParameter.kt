package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.validation.ISODateString

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "endDate",
    schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
    description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
)
annotation class EndDateParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = true,
)
