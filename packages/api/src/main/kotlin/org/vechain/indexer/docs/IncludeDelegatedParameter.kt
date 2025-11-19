package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

/**
 * Parameter annotation for the includeDelegated query parameter.
 *
 * Controls whether to include transactions where the address paid gas (delegated transactions).
 * Commonly used in transaction history queries.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "includeDelegated",
    schema = Schema(type = "boolean"),
    required = false,
    example = "false",
)
annotation class IncludeDelegatedParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Whether to include transactions the address paid gas for"
)
