package org.vechain.indexer.thor

object HexUtils {

    /** Optional prefix 0x */
    const val REGEX = "^(0x)?[0-9a-fA-F]+$"

    const val BLOCK_ID_REGEX = "^(0x)?[0-9a-fA-F]{64}\$"

    fun compare(hex1: String, hex2: String): Boolean {
        return normalise(hex1) == normalise(hex2)
    }

    fun isValid(hex: String): Boolean {
        return hex.matches(Regex(REGEX))
    }

    fun isValidBlockID(hex: String): Boolean {
        return hex.matches(Regex(BLOCK_ID_REGEX))
    }

    fun addPrefix(hex: String): String {
        return if (hex.startsWith("0x")) hex else "0x$hex"
    }

    fun removePrefix(hex: String): String {
        return if (hex.startsWith("0x")) hex.substring(2) else hex
    }

    /** Add prefix and lowercase. Used for queries in the DB */
    fun normalise(hex: String): String {
        return addPrefix(hex.lowercase())
    }

    fun toHex(num: Long, padToLength: Int? = null): String {
        require(padToLength == null || padToLength > 0) { "padToLength must be a positive number" }
        val hex = num.toString(16)
        val paddedHex = padToLength?.let { hex.padStart(it, '0') } ?: hex
        return addPrefix(paddedHex)
    }

    /** Parse hex string to Int, handling optional 0x prefix */
    fun toInt(hex: String): Int {
        require(isValid(hex)) { "Invalid hex string: $hex" }
        return removePrefix(hex).toInt(16)
    }
}
