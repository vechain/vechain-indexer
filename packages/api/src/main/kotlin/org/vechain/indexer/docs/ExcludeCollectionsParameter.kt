package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.thor.Address

/**
 * Parameter annotation for excluding collections (addresses) from query results.
 *
 * Accepts a list of valid addresses to exclude, with a maximum of 20 collections. This is commonly
 * used in NFT queries to filter out specific contracts.
 *
 * @see ValidAddressList
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "excludeCollections",
    array = ArraySchema(schema = Schema(type = "string", pattern = Address.REGEX), maxItems = 20),
    description = "The addresses of the collections to exclude. Max 20 collections.",
    required = false,
    example = "[\"0x1234567890123456789012345678901234567890\"]",
)
annotation class ExcludeCollectionsParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "The addresses of the collections to exclude. Max 20 collections."
)
