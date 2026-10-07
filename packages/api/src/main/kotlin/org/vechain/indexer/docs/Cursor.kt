package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
    `in` = ParameterIn.QUERY,
    name = "cursor",
    schema = Schema(type = "string"),
    description = "The `pagination.cursor` from the previous page. Omit for the first page.",
    required = false,
)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Cursor
