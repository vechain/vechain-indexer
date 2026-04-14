package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "eventName",
    array =
        ArraySchema(
            schema = Schema(type = "string", allowableValues = ["TRANSFER_NFT", "NFT_SALE"])
        ),
    description = "Filter by NFT history event names. Defaults to TRANSFER_NFT and NFT_SALE.",
    required = false,
)
annotation class NftHistoryEventNameParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String =
        "Filter by NFT history event names. Defaults to TRANSFER_NFT and NFT_SALE."
)
