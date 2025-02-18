package org.vechain.indexer.utils

import java.math.BigDecimal
import java.math.BigInteger

/** Utility functions for safely extracting values from parameter maps. */
object ParamUtils {

    /**
     * Retrieves a value as a String from a parameter map.
     * - Returns null if the key does not exist.
     * - Filters out empty strings.
     * - Converts numbers (BigDecimal, BigInteger, Int, Double) to a String.
     */
    fun Map<String, Any>?.getAsString(key: String): String? {
        return this?.get(key)?.let {
            when (it) {
                is String -> it.takeIf { it.isNotBlank() }
                is BigDecimal -> it.toPlainString() // Prevents scientific notation (e.g., 1E18)
                is BigInteger,
                is Number -> it.toString()
                else -> null
            }
        }
    }

    /**
     * Retrieves a value as an Integer from a parameter map.
     * - Returns null if the key does not exist.
     * - Converts String values to Int if possible.
     */
    fun Map<String, Any>?.getAsInt(key: String): Int? {
        return this?.get(key)?.let {
            when (it) {
                is Int -> it
                is String -> it.toIntOrNull() // Safe conversion
                is Number -> it.toInt()
                else -> null
            }
        }
    }
}
