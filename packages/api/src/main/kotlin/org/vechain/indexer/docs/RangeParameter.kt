package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

/**
 * Parameter annotation for time range preset path parameters.
 *
 * Used for endpoints that accept a time range like "1-hour", "1-day", "1-week", "1-month",
 * "1-year", or "all". The time series returned is sparsely populated, so it may not contain
 * consistent gaps between records.
 *
 * This annotation should be used on path parameters named "range".
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.PATH,
    name = "range",
    schema =
        Schema(
            type = "string",
            allowableValues = ["1-hour", "1-day", "1-week", "1-month", "1-year", "all"],
        ),
    description = "Time range preset to use for the query.",
    required = true,
    example = "1-day",
)
annotation class RangeParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Time range preset to use for the query.",
    @get:AliasFor(annotation = Parameter::class, attribute = "example")
    val example: String = "1-day",
)
