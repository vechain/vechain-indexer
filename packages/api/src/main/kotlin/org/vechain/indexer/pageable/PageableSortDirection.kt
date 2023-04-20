package org.vechain.indexer.pageable

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
    `in` = ParameterIn.QUERY,
    name = "direction",
    schema = Schema(type = "String"),
    description = "The sort direction (DESC or ASC)",
    required = false,
    example = "ASC"
)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PageableSortDirection
