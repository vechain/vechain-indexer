package org.vechain.indexer.pageable

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
    `in` = ParameterIn.QUERY,
    name = "size",
    schema = Schema(type = "Integer"),
    description = "The results page size",
    required = false,
    example = "20"
)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaginationSize
