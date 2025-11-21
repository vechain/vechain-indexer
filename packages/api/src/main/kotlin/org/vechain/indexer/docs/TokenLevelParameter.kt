package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

/**
 * Parameter annotation for Stargate token level query parameters.
 *
 * Used to filter results by NFT level. Supported levels are: Strength, Thunder, Mjolnir, VeThorX,
 * StrengthX, ThunderX, MjolnirX, Dawn, Lightning, Flash.
 *
 * If not provided, all levels will be included.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "level",
    schema =
        Schema(
            type = "string",
            allowableValues =
                [
                    "Strength",
                    "Thunder",
                    "Mjolnir",
                    "VeThorX",
                    "StrengthX",
                    "ThunderX",
                    "MjolnirX",
                    "Dawn",
                    "Lightning",
                    "Flash",
                ],
        ),
    required = false,
)
annotation class TokenLevelParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String =
        "Optional query parameter to filter by level. If not provided, all levels will be included."
)
