package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.vechain.indexer.exception.ExceptionResponse

/**
 * Adds a `503 Service Unavailable` response to the OpenAPI doc for endpoints that derive data from
 * the vechain.energy `PriceFeedOracle`. Mirrors the mapping in `ExceptionResponseConfig`
 * (`PriceFeedUnavailableException` → 503) so generated clients see a machine-readable response for
 * the oracle-unavailable failure path.
 */
@ApiResponses(
    value =
        [
            ApiResponse(
                responseCode = "503",
                description = "PriceFeedOracle is unreachable or returned an unusable response",
                content =
                    [
                        Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = ExceptionResponse::class),
                        ),
                        Content(
                            mediaType = "application/problem+json",
                            schema = Schema(implementation = ExceptionResponse::class),
                        ),
                    ],
            )
        ]
)
annotation class PriceOracleUnavailableResponse
