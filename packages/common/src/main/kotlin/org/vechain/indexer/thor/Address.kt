package org.vechain.indexer.thor

data class Address(val value: String) {

    companion object {
        const val REGEX = "^(0x)?[0-9a-fA-F]{40}\$"
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
    }

    fun isValid(): Boolean {
        return value.matches(Regex(REGEX))
    }

    fun isZero(): Boolean {
        return value == ZERO_ADDRESS
    }
}
