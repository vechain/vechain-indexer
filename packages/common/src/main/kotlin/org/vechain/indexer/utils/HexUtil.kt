package org.vechain.indexer.utils

object HexUtil {

    /**
     * Optional prefix 0x
     */
    const val REGEX = "^(0x)?[0-9a-fA-F]+$"

    fun isValid(hex: String): Boolean {
        return hex.matches(Regex(REGEX))
    }

    fun isNotValid(hex: String): Boolean {
        return !isValid(hex)
    }

    fun addPrefix(hex: String): String {
        return if (hex.startsWith("0x")) hex else "0x$hex"
    }

    /**
     * Add prefix and lowercase. Used for queries in the DB
     */
    fun normalise(hex: String): String {
        return addPrefix(hex.lowercase())
    }
}