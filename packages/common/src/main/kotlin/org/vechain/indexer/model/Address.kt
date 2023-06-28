package org.vechain.indexer.model

data class Address(val value: String) {

    companion object {
        const val REGEX = "^(0x)?[0-9a-fA-F]{40}\$"
    }

    fun isValid(): Boolean {
        return value.matches(Regex(REGEX))
    }
}
