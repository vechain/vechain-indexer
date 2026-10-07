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
                description = "Invalid request, such as a malformed parameter",
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
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden",
                content =
                    [
                        Content(mediaType = "application/json", schema = Schema(type = "string")),
                        Content(
                            mediaType = "application/problem+json",
                            schema = Schema(type = "string"),
                        ),
                    ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found",
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
            ),
            ApiResponse(
                responseCode = "500",
                description = "Temporarily unavailable; try again shortly",
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
            ),
        ]
)
annotation class CommonApiResponses
