package org.vechain.indexer.exception


import java.time.LocalDateTime
import java.time.ZoneOffset

data class ExceptionResponse(
    val status: Int,
    val message: String?,
    val error: String,
    val path: String,
    val timestamp: Number = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
)