package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "states",
    schema =
        Schema(
            type = "string",
            example = "Pending,Active",
            description =
                "Comma-separated list of ProposalState enum values to filter by (case-insensitive). Valid values: Pending, Active, Canceled, Defeated, Succeeded, Queued, Executed, DepositNotMet, InDevelopment, Completed.",
        ),
    description = "Filter by proposal states (comma-separated).",
)
annotation class ProposalStatesParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
)
