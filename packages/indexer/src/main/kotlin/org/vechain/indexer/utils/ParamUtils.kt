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

    /**
     * Retrieves a value as a Long from a parameter map.
     * - Returns null if the key does not exist.
     * - Converts String values to Long if possible.
     */
    fun GenericEventParameters.getAsLong(key: String): Long? =
        this.params[key]?.let {
            when (it) {
                is Long -> it
                is Int -> it.toLong()
                is String -> it.toLongOrNull()
                is Number -> it.toLong()
                else -> null
            }
        }

    /**
     * Retrieves a value as a Boolean from a parameter map.
     * - Returns null if the key does not exist.
     * - Converts String values to Boolean if possible.
     */
    fun GenericEventParameters.getAsBoolean(key: String): Boolean? =
        this.params[key]?.let {
            when (it) {
                is Boolean -> it
                is String -> it.toBoolean()
                else -> null
            }
        }

    /** Retrieves a value as a BigInterger from a parameter map. */
    fun GenericEventParameters.getAsBigInteger(key: String): BigInteger? =
        this.params[key]?.let {
            when (it) {
                is BigInteger -> it
                is Long -> BigInteger.valueOf(it)
                is Int -> BigInteger.valueOf(it.toLong())
                is String -> it.toBigIntegerOrNull()
                is Number -> BigInteger.valueOf(it.toLong())
                else -> null
            }
        }

    fun GenericEventParameters.getAsBigDecimal(key: String): BigDecimal? =
        this.params[key]?.let {
            when (it) {
                is BigDecimal -> it
                is BigInteger -> BigDecimal(it)
                is Long -> BigDecimal.valueOf(it)
                is Int -> BigDecimal.valueOf(it.toLong())
                is String -> it.toBigDecimalOrNull()
                is Number -> BigDecimal.valueOf(it.toLong())
                else -> null
            }
        }
}
