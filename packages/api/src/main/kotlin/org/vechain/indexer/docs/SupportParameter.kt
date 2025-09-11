package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "support",
    schema = Schema(type = "string", allowableValues = ["FOR", "AGAINST", "ABSTAIN"]),
    description = "Filter by support.",
)
annotation class SupportParameter(
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
