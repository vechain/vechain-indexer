package org.vechain.indexer.exception

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Schema(description = "Error response returned by the API")
data class ExceptionResponse(
    @Schema(
        description = "Unique identifier for this error",
        example = "7cecb690-631f-416f-930d-bb5828d02e67",
    )
    val id: String = UUID.randomUUID().toString(),
    @Schema(description = "HTTP status code", example = "400") val status: Int,
    @Schema(
        description = "Error message describing what went wrong",
        example = "The provided address is invalid",
    )
    val message: String?,
    @Schema(description = "Error type/category", example = "Bad Request") val error: String,
    @Schema(description = "The API path that was requested", example = "/api/v1/transfers")
    val path: String,
    @Schema(description = "Unix timestamp when the error occurred", example = "1762352201")
    val timestamp: Number = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
)
