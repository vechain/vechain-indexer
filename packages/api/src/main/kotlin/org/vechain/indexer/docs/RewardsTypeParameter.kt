package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

/**
 * Parameter annotation for rewardsType query parameters.
 *
 * Used to filter Stargate rewards by type: LEGACY or DELEGATION. If not provided or empty, all
 * reward types will be included.
 *
 * This annotation should be used in combination with validation logic to handle empty/null values.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "rewardsType",
    schema = Schema(type = "string", allowableValues = ["LEGACY", "DELEGATION"]),
    description =
        "Optional query parameter to filter rewards by type. If not provided, all types will be included.",
    required = false,
)
annotation class RewardsTypeParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String =
        "Optional query parameter to filter rewards by type. If not provided, all types will be included."
)
