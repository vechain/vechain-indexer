package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.b3tr.gm.GmLevelName

/**
 * Parameter annotation for Galaxy Member NFT level query parameters.
 *
 * Used to filter results by Galaxy Member level. Supported levels are: EARTH, MOON, MERCURY, VENUS,
 * MARS, JUPITER, SATURN, URANUS, NEPTUNE, GALAXY, or ALL for no filtering.
 *
 * If not provided, all levels will be included.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "level",
    description = "Optional level to filter by",
    schema = Schema(implementation = GmLevelName::class),
    required = false,
)
annotation class GmNftLevelParameter
