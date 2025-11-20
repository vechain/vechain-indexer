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
@Repeatable
@Parameter(schema = Schema(type = "integer", format = "int64", minimum = "0"))
annotation class BeforeParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "name") val name: String = "before",
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Return records before this time (Unix time in seconds).",
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
    @get:AliasFor(annotation = Parameter::class, attribute = "example")
    val example: String = "1704153600",
)
