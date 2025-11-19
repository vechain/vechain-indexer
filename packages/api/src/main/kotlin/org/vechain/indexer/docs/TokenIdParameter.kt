package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.validation.ValidTokenId

/**
 * Parameter annotation for tokenId path or query parameters.
 *
 * Accepts both hex format (0xABC, 0x123) and decimal format (123, 456) tokenIds. The regex pattern
 * `^(0x)?[A-Fa-f0-9]+$` covers both NFT tokenIds (hex) and Stargate tokenIds (decimal).
 *
 * This annotation should be used in combination with @ValidTokenId validator on the method
 * parameter.
 *
 * @see ValidTokenId
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "tokenId",
    schema = Schema(type = "string", pattern = "^(0x)?[A-Fa-f0-9]+$"),
    description = "A valid tokenId",
    required = false,
)
annotation class TokenIdParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = true,
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "A valid tokenId",
    @get:AliasFor(annotation = Parameter::class, attribute = "example") val example: String = "",
)
