package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "searchBy",
    array =
        ArraySchema(
            schema =
                Schema(
                    type = "string",
                    allowableValues = ["to", "from", "origin", "gasPayer"],
                    description = "Address fields to match. Defaults to `to`, `from` and `origin`.",
                )
        ),
    required = false,
)
annotation class SearchByParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Array of fields to search by."
)
