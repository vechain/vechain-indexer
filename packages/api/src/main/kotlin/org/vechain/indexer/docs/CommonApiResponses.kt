package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.vechain.indexer.exception.ExceptionResponse

// Define reusable ApiResponse annotations
@ApiResponses(
    value =
        [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(
                responseCode = "400",
                description = "Validation errors occurred, eg: invalid input",
                content =
                    [
                        Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = ExceptionResponse::class),
                        )
                    ],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Service not available",
                content =
                    [
                        Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = ExceptionResponse::class),
                        )
                    ],
            ),
        ]
)
annotation class CommonApiResponses
