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
                        ),
                        Content(
                            mediaType = "application/problem+json",
                            schema = Schema(implementation = ExceptionResponse::class),
                        ),
                        Content(mediaType = "text/html", schema = Schema(type = "string")),
                    ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Access to the requested resource is forbidden",
                content =
                    [
                        Content(mediaType = "application/json", schema = Schema(type = "string")),
                        Content(
                            mediaType = "application/problem+json",
                            schema = Schema(type = "string"),
                        ),
                        Content(mediaType = "text/html", schema = Schema(type = "string")),
                    ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested resource was not found",
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
