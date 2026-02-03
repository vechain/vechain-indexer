package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "eventType",
    array =
        ArraySchema(
            schema =
                Schema(
                    type = "string",
                    allowableValues = ["VET", "FUNGIBLE_TOKEN", "NFT", "SEMI_FUNGIBLE_TOKEN"],
                )
        ),
    description = "Filter by transfer event type(s)",
    required = false,
)
annotation class TransferEventTypeParameter
