package org.vechain.indexer.config.metrics

import java.util.Locale
import org.vechain.indexer.Status

fun String.toReadableEnumLabel(): String =
    split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }

fun Status.toStatusCode(): Double =
    when (this) {
        Status.NOT_INITIALISED -> 0.0
        Status.INITIALISED -> 1.0
        Status.SYNCING -> 2.0
        Status.FAST_SYNCING -> 3.0
        Status.SHUT_DOWN -> 5.0
        Status.FULLY_SYNCED -> 6.0
    }
