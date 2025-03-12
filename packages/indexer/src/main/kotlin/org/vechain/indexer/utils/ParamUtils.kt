package org.vechain.indexer.utils

import java.math.BigDecimal
import java.math.BigInteger
import org.vechain.indexer.event.model.generic.GenericEventParameters

/** Utility functions for safely extracting values from parameter maps. */
object ParamUtils {
    /**
     * Retrieves a value as a String from a parameter map.
     * - Returns null if the key does not exist.
     * - Filters out empty strings.
     * - Converts numbers (BigDecimal, BigInteger, Int, Double) to a String.
     */
    fun GenericEventParameters.getAsString(key: String): String? =
        this.params[key]?.let {
            when (it) {
                is String -> it.takeIf { it.isNotBlank() }
                is BigDecimal -> it.toPlainString()
                is BigInteger,
                is Number, -> it.toString()
                else -> null
            }
        }

    /**
     * Retrieves a value as an Integer from a parameter map.
     * - Returns null if the key does not exist.
     * - Converts String values to Int if possible.
     */
    fun GenericEventParameters.getAsInt(key: String): Int? =
        this.params[key]?.let {
            when (it) {
                is Int -> it
                is String -> it.toIntOrNull() // Safe conversion
                is Number -> it.toInt()
                else -> null
            }
        }
}
