package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "states",
    array =
        ArraySchema(
            schema =
                Schema(
                    type = "string",
                    allowableValues =
                        [
                            "Pending",
                            "Active",
                            "Canceled",
                            "Defeated",
                            "Succeeded",
                            "Queued",
                            "Executed",
                            "DepositNotMet",
                            "InDevelopment",
                            "Completed",
                        ],
                    description = "ProposalState enum values (case-insensitive)",
                )
        ),
    description = "Filter by proposal states.",
)
annotation class ProposalStatesParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
)
