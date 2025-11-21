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
 * @see org.vechain.indexer.validation.ValidAddressList
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    array = ArraySchema(schema = Schema(type = "string", pattern = Address.REGEX), maxItems = 20),
    example = "[\"0x1234567890123456789012345678901234567890\"]",
)
annotation class AddressListParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "name") val name: String = "addresses",
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "A list of addresses. Max 20 collections.",
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
)
