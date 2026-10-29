package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "sort",
    description =
        "`newest` orders by the block the listing was made in, `price` by the asking price; " +
            "`direction` applies to either.",
    schema =
        Schema(type = "string", allowableValues = ["newest", "price"], defaultValue = "newest"),
    required = false,
)
annotation class ListingSortParameter
