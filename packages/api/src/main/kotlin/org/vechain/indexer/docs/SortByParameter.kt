package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(name = "sortBy", description = "The sort by field")
annotation class SortByParameter(
    val schema: Schema = Schema(type = "string", allowableValues = []),
    val `in`: ParameterIn = ParameterIn.QUERY,
    val required: Boolean = false,
)
